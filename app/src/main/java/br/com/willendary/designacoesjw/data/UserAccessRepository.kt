package br.com.willendary.designacoesjw.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.UUID

class UserAccessRepository {
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
        profileListener = users.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "Não foi possível carregar as permissões da conta.")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                onProfile(fromUser(snapshot.data ?: emptyMap(), uid))
                return@addSnapshotListener
            }

            // Novas contas começam como visualizador. A promoção para administrador
            // deve ser feita por um administrador existente ou pelo bootstrap inicial.
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
            active = data["active"] as? Boolean ?: true
        )
    }

    private fun toMap(profile: UserProfile) = mapOf(
        "email" to profile.email,
        "name" to profile.name,
        "role" to profile.role,
        "permissions" to profile.permissions.toList(),
        "active" to profile.active
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
