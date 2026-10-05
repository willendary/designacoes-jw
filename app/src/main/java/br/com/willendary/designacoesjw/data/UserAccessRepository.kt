package br.com.willendary.designacoesjw.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import android.util.Log
import java.util.UUID

class UserAccessRepository {
    private companion object {
        const val TAG_CONVITE = "UserAccess"
    }
    private val firestore = FirebaseFirestore.getInstance()
    private val users = firestore.collection("users")
    private val invitations = firestore.collection("workspaces").document("designacoes-jw").collection("invitations")
    private var profileListener: ListenerRegistration? = null
    private var invitationsListener: ListenerRegistration? = null

    fun observeCurrentUser(
        uid: String,
        email: String,
        onProfile: (UserProfile) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        profileListener?.remove()
        val access = firestore.collection("workspaces").document("designacoes-jw").collection("settings").document("access")
        access.get().addOnSuccessListener { accessSnapshot ->
            val bootstrapUid = accessSnapshot.data?.get("bootstrapUid")?.toString().orEmpty()
            val isBootstrapAdmin = bootstrapUid == uid

            profileListener = users.document(uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "Não foi possível carregar as permissões da conta.")
                    return@addSnapshotListener
                }

                if (isBootstrapAdmin) {
                    val profile = UserProfile(
                        uid = uid,
                        email = email,
                        name = snapshot?.data?.get("name")?.toString() ?: "",
                        role = "admin",
                        permissions = AppPermissions.all,
                        active = true
                    )
                    users.document(uid).set(toMap(profile), SetOptions.merge())
                    onProfile(profile)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    onProfile(fromUser(snapshot.data ?: emptyMap(), uid))
                    return@addSnapshotListener
                }

                val profile = UserProfile(
                    uid = uid,
                    email = email,
                    name = "",
                    role = "viewer",
                    permissions = setOf(AppPermissions.VIEW_ASSIGNMENTS, AppPermissions.EXPORT_REPORTS)
                )
                users.document(uid).set(toMap(profile), SetOptions.merge())
                onProfile(profile)
            }
        }.addOnFailureListener {
            // Sem documento de bootstrap, segue o fluxo normal de criação do perfil.
            profileListener = users.document(uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "Não foi possível carregar as permissões da conta.")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    onProfile(fromUser(snapshot.data ?: emptyMap(), uid))
                    return@addSnapshotListener
                }
                val profile = UserProfile(
                    uid = uid,
                    email = email,
                    name = "",
                    role = "viewer",
                    permissions = setOf(AppPermissions.VIEW_ASSIGNMENTS, AppPermissions.EXPORT_REPORTS)
                )
                users.document(uid).set(toMap(profile), SetOptions.merge())
                onProfile(profile)
            }
        }
    }

    fun observeInvitations(onInvitations: (List<Invitation>) -> Unit, onError: (String) -> Unit = {}) {
        invitationsListener?.remove()
        invitationsListener = invitations.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "Não foi possível carregar os convites.")
                return@addSnapshotListener
            }
            onInvitations(snapshot?.documents?.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                Invitation(
                    id = doc.id,
                    email = data["email"]?.toString() ?: return@mapNotNull null,
                    name = data["name"]?.toString() ?: "",
                    permissions = (data["permissions"] as? List<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet(),
                    status = data["status"]?.toString() ?: "pending",
                    createdBy = data["createdBy"]?.toString() ?: "",
                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
                )
            } ?: emptyList())
        }
    }

    fun saveInvitation(
        email: String,
        name: String,
        permissions: Set<String>,
        createdBy: String,
        onResult: (Invitation?, String?) -> Unit
    ) {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) {
            onResult(null, "Informe um e-mail.")
            return
        }
        val id = UUID.randomUUID().toString()
        val invitation = Invitation(
            id = id,
            email = normalized,
            name = name.trim(),
            permissions = permissions,
            status = "pending",
            createdBy = createdBy,
            createdAt = System.currentTimeMillis()
        )
        invitations.document(id).set(toMap(invitation))
            .addOnSuccessListener { onResult(invitation, null) }
            .addOnFailureListener { onResult(null, it.localizedMessage ?: "Não foi possível criar o convite.") }
    }

    fun claimInvitation(invitationId: String, uid: String, email: String, onResult: (String?) -> Unit = {}) {
        invitations.document(invitationId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    onResult("Convite não encontrado ou expirado.")
                    return@addOnSuccessListener
                }
                val data = snapshot.data ?: run {
                    onResult("Convite inválido.")
                    return@addOnSuccessListener
                }
                val invitedEmail = data["email"]?.toString()?.trim()?.lowercase()
                if (invitedEmail != email.trim().lowercase()) {
                    onResult("O e-mail desta conta ($email) não corresponde ao convite ($invitedEmail).")
                    return@addOnSuccessListener
                }
                if (data["status"]?.toString() == "accepted" && data["acceptedBy"]?.toString() == uid) {
                    onResult(null)
                    return@addOnSuccessListener
                }
                if (data["status"]?.toString() != "pending") {
                    onResult("Este convite já foi utilizado.")
                    return@addOnSuccessListener
                }

                val permissions = (data["permissions"] as? List<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
                val profile = UserProfile(
                    uid = uid,
                    email = email.trim().lowercase(),
                    name = data["name"]?.toString() ?: "",
                    role = "custom",
                    permissions = permissions,
                    active = true,
                    invitationId = invitationId
                )
                // A ordem é perfil primeiro, convite depois, e é deliberada.
                //
                // O inverso (marcar o convite como usado e só então gravar o
                // perfil) prende o usuário: a regra do Firestore só deixa um
                // convite `pending` virar `accepted` uma vez, então uma falha na
                // segunda escrita deixaria o convite consumido e sem perfil, sem
                // como tentar de novo.
                //
                // Com esta ordem, uma falha no update do convite deixa o perfil
                // gravado e o convite ainda `pending` — e o caminho de retry
                // acima (status `pending` + `set` com merge) refaz o trabalho.
                users.document(uid).set(toMap(profile), SetOptions.merge())
                    .addOnSuccessListener {
                        invitations.document(invitationId).update(mapOf(
                            "status" to "accepted",
                            "acceptedBy" to uid,
                            "acceptedAt" to System.currentTimeMillis()
                        )).addOnSuccessListener { onResult(null) }
                          .addOnFailureListener { erro ->
                              // Antes isto era `onResult(null)`, o MESMO valor do
                              // sucesso: o usuário entrava na congregação, o app
                              // confirmava "tudo certo", e o convite continuava
                              // `pending` do lado do administrador — que via um
                              // convite aparentemente não usado.
                              registrarFalhaAoAceitarConvite(erro)
                              onResult(
                                  "Seu acesso foi liberado, mas não consegui confirmar o convite. " +
                                      "Tente aceitar de novo em instantes; se persistir, peça ao responsável " +
                                      "para conferir seu acesso."
                              )
                          }
                    }
                    .addOnFailureListener {
                        registrarFalhaAoAceitarConvite(it)
                        onResult(
                            "Não foi possível liberar seu acesso. Verifique sua conexão e tente de novo."
                        )
                    }
            }
            .addOnFailureListener {
                registrarFalhaAoAceitarConvite(it)
                onResult(
                    "Não foi possível validar este convite. Verifique sua conexão e tente de novo."
                )
            }
    }

    /**
     * Falha ao aceitar convite vai para o log, com a causa técnica.
     *
     * A tela recebe uma frase que diz o que fazer; aqui fica o motivo. Sem esta
     * separação, o usuário vê "não foi possível validar" sem causa e ninguém
     * descobre se foi regra, token ou rede.
     *
     * `Log.e` e não `CrashLog.gravar`: aquele exige `Context`, e o caminho de
     * erro é justamente o que não pode depender de contexto nenhum.
     */
    private fun registrarFalhaAoAceitarConvite(e: Exception) {
        Log.e(TAG_CONVITE, "Falha ao aceitar convite: ${e.javaClass.simpleName}: ${e.message}")
    }

    fun updateUser(profile: UserProfile, onResult: (String?) -> Unit) {
        users.document(profile.uid).set(toMap(profile), SetOptions.merge())
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(it.localizedMessage ?: "Não foi possível salvar as permissões.") }
    }

    fun observeAllUsers(onUsers: (List<UserProfile>) -> Unit, onError: (String) -> Unit = {}): ListenerRegistration {
        return users.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "Não foi possível carregar os usuários.")
                return@addSnapshotListener
            }
            onUsers(snapshot?.documents?.mapNotNull { doc ->
                doc.data?.let { fromUser(it, doc.id) }
            } ?: emptyList())
        }
    }

    fun close() {
        profileListener?.remove()
        profileListener = null
        invitationsListener?.remove()
        invitationsListener = null
    }

    private fun fromUser(data: Map<String, Any>, uid: String): UserProfile {
        return UserProfile(
            uid = uid,
            email = data["email"]?.toString() ?: "",
            name = data["name"]?.toString() ?: "",
            role = data["role"]?.toString() ?: "viewer",
            permissions = (data["permissions"] as? List<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet(),
            active = data["active"] as? Boolean ?: true,
            invitationId = data["invitationId"]?.toString() ?: ""
        )
    }

    private fun toMap(profile: UserProfile) = mapOf(
        "email" to profile.email,
        "name" to profile.name,
        "role" to profile.role,
        "permissions" to profile.permissions.toList(),
        "active" to profile.active,
        "invitationId" to profile.invitationId
    )

    private fun toMap(invitation: Invitation) = mapOf(
        "email" to invitation.email,
        "name" to invitation.name,
        "permissions" to invitation.permissions.toList(),
        "status" to invitation.status,
        "createdBy" to invitation.createdBy,
        "createdAt" to invitation.createdAt
    )
}
