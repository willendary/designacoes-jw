package br.com.willendary.designacoesjw.desktop.firebase

import br.com.willendary.designacoesjw.data.*
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

object DesktopFirestoreClient {
    private val json = Json { ignoreUnknownKeys = true }
    private const val BASE_URL =
        "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/(default)/documents/workspaces/designacoes-jw"

    /** Número máximo de tentativas por requisição (1 inicial + retries). */
    private const val MAX_ATTEMPTS = 3

    /** Erro de HTTP do Firestore, carregando o status code para retry seletivo. */
    private class FirestoreException(val statusCode: Int, message: String) : Exception(message)

    /** Backoff exponencial: 500ms, 1s, 2s. */
    private fun backoffDelay(attempt: Int): Long = 500L shl (attempt - 1)

    fun fetchCloudStore(idToken: String): Result<Store> = runCatching {
        val brothers = fetchCollection(idToken, "brothers") { parseBrother(it) }
        val privileges = fetchCollection(idToken, "privileges") { parsePrivilege(it) }
        val meetings = fetchCollection(idToken, "meetings") { parseMeeting(it) }
        val publicTalks = fetchCollection(idToken, "publicTalks") { parsePublicTalk(it) }
        val groups = fetchCollection(idToken, "fieldServiceGroups") { parseFieldServiceGroup(it) }
        val cleaning = fetchCollection(idToken, "cleaningSchedules") { parseCleaningSchedule(it) }
        val settings = fetchDocument(idToken, "settings/main")

        val firstDay = settings?.get("firstDay")?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toIntOrNull() ?: 3
        val secondDay = settings?.get("secondDay")?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toIntOrNull() ?: 6

        Store(
            brothers = brothers,
            privileges = privileges,
            meetings = meetings,
            firstDay = firstDay,
            secondDay = secondDay,
            publicTalks = publicTalks,
            fieldServiceGroups = groups,
            cleaningSchedules = cleaning
        )
    }

    fun pushBrothers(idToken: String, brothers: List<Brother>): Result<Unit> = runCatching {
        brothers.forEach { brother ->
            val docId = brother.id.toString()
            val fields = buildJsonObject {
                put("id", buildJsonObject { put("integerValue", brother.id.toString()) })
                put("name", buildJsonObject { put("stringValue", brother.name) })
                put("phone", buildJsonObject { put("stringValue", brother.phone) })
                put("active", buildJsonObject { put("booleanValue", brother.active) })
                put("role", buildJsonObject { put("stringValue", brother.role.name) })
                put("gender", buildJsonObject { put("stringValue", brother.gender.name) })
                // Campos teocráticos: sem isto as regras de não batizado,
                // aprendiz e leitor valem só em memória e voltam ao default
                // quando o app recarrega.
                put("baptized", buildJsonObject { put("booleanValue", brother.baptized) })
                put("trainee", buildJsonObject { put("booleanValue", brother.trainee) })
                put("isReader", buildJsonObject { put("booleanValue", brother.isReader) })
                put("isSentinelReader", buildJsonObject { put("booleanValue", brother.isSentinelReader) })
                brother.groupId?.let {
                    put("groupId", buildJsonObject { put("integerValue", it.toString()) })
                }
                put("privileges", buildJsonObject {
                    put("arrayValue", buildJsonObject {
                        put("values", buildJsonArray {
                            brother.privileges.forEach { pid ->
                                add(buildJsonObject { put("integerValue", pid.toString()) })
                            }
                        })
                    })
                })
                put("unavailabilities", buildJsonObject {
                    put("arrayValue", buildJsonObject {
                        put("values", buildJsonArray {
                            brother.unavailabilities.forEach { u ->
                                add(buildJsonObject {
                                    put("mapValue", buildJsonObject {
                                        put("fields", buildJsonObject {
                                            put("id", buildJsonObject { put("integerValue", u.id.toString()) })
                                            put("startDate", buildJsonObject { put("stringValue", u.startDate) })
                                            put("endDate", buildJsonObject { put("stringValue", u.endDate) })
                                            put("reason", buildJsonObject { put("stringValue", u.reason) })
                                        })
                                    })
                                })
                            }
                        })
                    })
                })
            }
            patchDocument(idToken, "brothers", docId, fields)
        }
    }

    fun pushPrivileges(idToken: String, privileges: List<Privilege>): Result<Unit> = runCatching {
        privileges.forEach { priv ->
            val docId = priv.id.toString()
            val fields = buildJsonObject {
                put("id", buildJsonObject { put("integerValue", priv.id.toString()) })
                put("name", buildJsonObject { put("stringValue", priv.name) })
                put("quantity", buildJsonObject { put("integerValue", priv.quantity.toString()) })
                put("active", buildJsonObject { put("booleanValue", priv.active) })
                put("minRole", buildJsonObject { put("stringValue", priv.minRole.name) })
                put("maleOnly", buildJsonObject { put("booleanValue", priv.maleOnly) })
                put("kind", buildJsonObject { put("stringValue", priv.kind.name) })
                put("readerGrant", buildJsonObject { put("stringValue", priv.readerGrant.name) })
                put("allowedStatus", buildJsonObject {
                    put("arrayValue", buildJsonObject {
                        put("values", buildJsonArray {
                            priv.allowedStatus.forEach { s ->
                                add(buildJsonObject { put("stringValue", s.name) })
                            }
                        })
                    })
                })
                put("allowedDays", buildJsonObject {
                    put("arrayValue", buildJsonObject {
                        put("values", buildJsonArray {
                            priv.allowedDays.forEach { d ->
                                add(buildJsonObject { put("integerValue", d.toString()) })
                            }
                        })
                    })
                })
            }
            patchDocument(idToken, "privileges", docId, fields)
        }
    }

    fun pushMeetings(idToken: String, meetings: List<Meeting>): Result<Unit> = runCatching {
        meetings.forEach { m ->
            val docId = m.id.toString()
            val fields = buildJsonObject {
                put("id", buildJsonObject { put("integerValue", m.id.toString()) })
                put("date", buildJsonObject { put("stringValue", m.date) })
                put("type", buildJsonObject { put("stringValue", m.type) })
                put("assignments", buildJsonObject {
                    put("arrayValue", buildJsonObject {
                        put("values", buildJsonArray {
                            m.assignments.forEach { a ->
                                add(buildJsonObject {
                                    put("mapValue", buildJsonObject {
                                        put("fields", buildJsonObject {
                                            put("privilegeId", buildJsonObject { put("integerValue", a.privilegeId.toString()) })
                                            put("brotherId", buildJsonObject { put("integerValue", a.brotherId.toString()) })
                                        })
                                    })
                                })
                            }
                        })
                    })
                })
                put("blockedBrotherIds", buildJsonObject {
                    put("arrayValue", buildJsonObject {
                        put("values", buildJsonArray {
                            m.blockedBrotherIds.forEach { bid ->
                                add(buildJsonObject { put("integerValue", bid.toString()) })
                            }
                        })
                    })
                })
                if (m.theme.isNotBlank()) {
                    put("theme", buildJsonObject { put("stringValue", m.theme) })
                }
                put("programAssignments", buildJsonObject {
                    put("arrayValue", buildJsonObject {
                        put("values", buildJsonArray {
                            m.programAssignments.forEach { pa ->
                                add(buildJsonObject {
                                    put("mapValue", buildJsonObject {
                                        put("fields", buildJsonObject {
                                            put("item", buildJsonObject { put("integerValue", pa.item.toString()) })
                                            put("brotherIds", buildJsonObject {
                                                put("arrayValue", buildJsonObject {
                                                    put("values", buildJsonArray {
                                                        pa.brotherIds.forEach { bid ->
                                                            add(buildJsonObject { put("integerValue", bid.toString()) })
                                                        }
                                                    })
                                                })
                                            })
                                        })
                                    })
                                })
                            }
                        })
                    })
                })
                if (m.program.isNotEmpty()) {
                    // Grava o formato novo (mapas), não a string legada.
                    put("program", buildJsonObject {
                        put("arrayValue", buildJsonObject {
                            put("values", buildJsonArray {
                                m.program.forEach { p ->
                                    add(buildJsonObject {
                                        put("mapValue", buildJsonObject {
                                            put("fields", buildJsonObject {
                                                put("section", buildJsonObject { put("stringValue", p.section) })
                                                put("number", buildJsonObject { put("integerValue", p.number.toString()) })
                                                put("title", buildJsonObject { put("stringValue", p.title) })
                                                put("minutes", buildJsonObject { put("integerValue", p.minutes.toString()) })
                                                put("kind", buildJsonObject { put("stringValue", p.kind.name) })
                                            })
                                        })
                                    })
                                }
                            })
                        })
                    })
                }
            }
            patchDocument(idToken, "meetings", docId, fields)
        }
    }

    fun pushPublicTalks(idToken: String, talks: List<PublicTalk>): Result<Unit> = runCatching {
        talks.forEach { t ->
            val docId = t.id.toString()
            val fields = buildJsonObject {
                put("id", buildJsonObject { put("integerValue", t.id.toString()) })
                put("date", buildJsonObject { put("stringValue", t.date) })
                t.themeNumber?.let { put("themeNumber", buildJsonObject { put("integerValue", it.toString()) }) }
                put("themeTitle", buildJsonObject { put("stringValue", t.themeTitle) })
                put("speakerName", buildJsonObject { put("stringValue", t.speakerName) })
                put("speakerCongregation", buildJsonObject { put("stringValue", t.speakerCongregation) })
                put("speakerPhone", buildJsonObject { put("stringValue", t.speakerPhone) })
                t.hospitalityBrotherId?.let { put("hospitalityBrotherId", buildJsonObject { put("integerValue", it.toString()) }) }
                put("hospitalityNotes", buildJsonObject { put("stringValue", t.hospitalityNotes) })
                put("confirmed", buildJsonObject { put("booleanValue", t.confirmed) })
            }
            patchDocument(idToken, "publicTalks", docId, fields)
        }
    }

    fun pushCleaningSchedules(idToken: String, schedules: List<CleaningSchedule>): Result<Unit> = runCatching {
        schedules.forEach { s ->
            val docId = s.id.toString()
            val fields = buildJsonObject {
                put("id", buildJsonObject { put("integerValue", s.id.toString()) })
                put("weekDate", buildJsonObject { put("stringValue", s.weekDate) })
                s.groupId?.let { put("groupId", buildJsonObject { put("integerValue", it.toString()) }) }
                put("details", buildJsonObject { put("stringValue", s.details) })
                put("completed", buildJsonObject { put("booleanValue", s.completed) })
            }
            patchDocument(idToken, "cleaningSchedules", docId, fields)
        }
    }

    fun pushFieldServiceGroups(idToken: String, groups: List<FieldServiceGroup>): Result<Unit> = runCatching {
        groups.forEach { g ->
            val docId = g.id.toString()
            val fields = buildJsonObject {
                put("id", buildJsonObject { put("integerValue", g.id.toString()) })
                put("number", buildJsonObject { put("integerValue", g.number.toString()) })
                put("name", buildJsonObject { put("stringValue", g.name) })
                g.overseerBrotherId?.let { put("overseerBrotherId", buildJsonObject { put("integerValue", it.toString()) }) }
                g.assistantBrotherId?.let { put("assistantBrotherId", buildJsonObject { put("integerValue", it.toString()) }) }
            }
            patchDocument(idToken, "fieldServiceGroups", docId, fields)
        }
    }

    fun pushScheduleSettings(idToken: String, firstDay: Int, secondDay: Int): Result<Unit> = runCatching {
        val fields = buildJsonObject {
            put("firstDay", buildJsonObject { put("integerValue", firstDay.toString()) })
            put("secondDay", buildJsonObject { put("integerValue", secondDay.toString()) })
        }
        patchDocument(idToken, "settings", "main", fields)
    }

    private fun <T> fetchCollection(idToken: String, collectionName: String, parser: (JsonObject) -> T?): List<T> {
        val resp = request(idToken, URI("$BASE_URL/$collectionName?pageSize=300"), "GET")
        val root = json.parseToJsonElement(resp).jsonObject
        val docs = root["documents"]?.jsonArray ?: return emptyList()
        return docs.mapNotNull { doc ->
            val fields = doc.jsonObject["fields"]?.jsonObject ?: return@mapNotNull null
            parser(fields)
        }
    }

    private fun fetchDocument(idToken: String, documentPath: String): JsonObject? {
        val resp = try {
            request(idToken, URI("$BASE_URL/$documentPath"), "GET")
        } catch (e: FirestoreException) {
            // 404 = documento ainda não existe (ex.: settings/main em workspace
            // recém-criado). Não é erro: devolve null para aplicar os defaults.
            if (e.statusCode == 404) return null
            throw e
        }
        return json.parseToJsonElement(resp).jsonObject["fields"]?.jsonObject
    }

    private fun patchDocument(idToken: String, collection: String, docId: String, fields: JsonObject) {
        val body = buildJsonObject { put("fields", fields) }.toString()
        request(idToken, URI("$BASE_URL/$collection/$docId"), "PATCH", body)
    }

    /**
     * Executa uma requisição HTTP ao Firestore e devolve o corpo em caso de 2xx.
     * Qualquer resposta fora de 2xx vira [FirestoreException] com status + corpo,
     * para que o erro chegue à UI em vez de virar um Store vazio "Sincronizado".
     * 409 (CONFLICT/aborted) e 429 (RESOURCE_EXHAUSTED) são retentados com backoff
     * exponencial, no máximo [MAX_ATTEMPTS] tentativas.
     */
    private fun request(idToken: String, url: URI, method: String, body: String? = null): String {
        var attempt = 0
        while (true) {
            val conn = (url.toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = method
                setRequestProperty("Authorization", "Bearer $idToken")
                connectTimeout = 8000
                readTimeout = 8000
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
            }
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code in 200..299) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
            val errBody = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: ""
            if ((code == 409 || code == 429) && attempt < MAX_ATTEMPTS - 1) {
                attempt++
                Thread.sleep(backoffDelay(attempt))
                continue
            }
            throw FirestoreException(code, "HTTP $code: $errBody")
        }
    }

    private fun parseBrother(fields: JsonObject): Brother? {
        val id = fields["id"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val name = fields["name"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: return null
        val phone = fields["phone"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
        val active = fields["active"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        val roleStr = fields["role"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: "PUBLISHER"
        val role = runCatching { BrotherRole.valueOf(roleStr) }.getOrDefault(BrotherRole.PUBLISHER)
        val genderStr = fields["gender"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: "MALE"
        val gender = runCatching { Gender.valueOf(genderStr) }.getOrDefault(Gender.MALE)
        val groupId = fields["groupId"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull()
        val baptized = fields["baptized"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        val trainee = fields["trainee"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val isReader = fields["isReader"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val isSentinelReader = fields["isSentinelReader"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

        val privArray = fields["privileges"]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
        val privileges = privArray?.mapNotNull { it.jsonObject["integerValue"]?.jsonPrimitive?.content?.toLongOrNull() }?.toSet() ?: emptySet()

        val unavailArray = fields["unavailabilities"]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
        val unavails = unavailArray?.mapNotNull { uVal ->
            val uFields = uVal.jsonObject["mapValue"]?.jsonObject?.get("fields")?.jsonObject ?: return@mapNotNull null
            val uid = uFields["id"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val start = uFields["startDate"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: return@mapNotNull null
            val end = uFields["endDate"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: return@mapNotNull null
            val reason = uFields["reason"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
            UnavailablePeriod(uid, start, end, reason)
        } ?: emptyList()

        return Brother(
            id = id,
            name = name,
            phone = phone,
            privileges = privileges,
            active = active,
            role = role,
            unavailabilities = unavails,
            gender = gender,
            groupId = groupId,
            baptized = baptized,
            trainee = trainee,
            isReader = isReader,
            isSentinelReader = isSentinelReader
        )
    }

    private fun parsePrivilege(fields: JsonObject): Privilege? {
        val id = fields["id"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val name = fields["name"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: return null
        val qty = fields["quantity"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val active = fields["active"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        val roleStr = fields["minRole"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: "PUBLISHER"
        val minRole = runCatching { BrotherRole.valueOf(roleStr) }.getOrDefault(BrotherRole.PUBLISHER)
        val maleOnly = fields["maleOnly"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true

        val daysArray = fields["allowedDays"]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
        val allowedDays = daysArray?.mapNotNull { it.jsonObject["integerValue"]?.jsonPrimitive?.content?.toIntOrNull() }?.toSet() ?: emptySet()

        val kind = runCatching { PartKind.valueOf(fields["kind"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: "INDIVIDUAL") }
            .getOrDefault(PartKind.INDIVIDUAL)
        val readerGrant = runCatching { ReaderGrant.valueOf(fields["readerGrant"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: "NONE") }
            .getOrDefault(ReaderGrant.NONE)
        val statusArray = fields["allowedStatus"]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
        // Ausente = todos permitidos, que é o comportamento anterior.
        val allowedStatus = statusArray
            ?.mapNotNull { runCatching { BrotherStatus.valueOf(it.jsonObject["stringValue"]?.jsonPrimitive?.content.orEmpty()) }.getOrNull() }
            ?.toSet()
            ?: BrotherStatus.entries.toSet()

        return Privilege(
            id = id, name = name, quantity = qty, active = active,
            allowedDays = allowedDays, minRole = minRole, maleOnly = maleOnly,
            kind = kind, allowedStatus = allowedStatus, readerGrant = readerGrant
        )
    }

    private fun parseMeeting(fields: JsonObject): Meeting? {
        val id = fields["id"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val date = fields["date"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: return null
        val type = fields["type"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: "Reunião"

        val assignArray = fields["assignments"]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
        val assignments = assignArray?.mapNotNull { aVal ->
            val aFields = aVal.jsonObject["mapValue"]?.jsonObject?.get("fields")?.jsonObject ?: return@mapNotNull null
            val pid = aFields["privilegeId"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            val bid = aFields["brotherId"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            Assignment(pid, bid)
        } ?: emptyList()

        val blockedArray = fields["blockedBrotherIds"]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
        val blocked = blockedArray?.mapNotNull { it.jsonObject["integerValue"]?.jsonPrimitive?.content?.toLongOrNull() }?.toSet() ?: emptySet()

        val theme = fields["theme"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
        val programArray = fields["program"]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
        // Aceita os dois formatos: mapValue (novo) e stringValue (legado "N. Título (M min)").
        val program = programArray?.mapNotNull { pVal ->
            val mapFields = pVal.jsonObject["mapValue"]?.jsonObject?.get("fields")?.jsonObject
            if (mapFields != null) {
                ProgramItem(
                    section = mapFields["section"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: "",
                    number = mapFields["number"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    title = mapFields["title"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: "",
                    minutes = mapFields["minutes"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    // Sem default explicito, parte antiga cai em INDIVIDUAL.
                    kind = runCatching {
                        PartKind.valueOf(
                            mapFields["kind"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: "INDIVIDUAL"
                        )
                    }.getOrDefault(PartKind.INDIVIDUAL)
                )
            } else {
                pVal.jsonObject["stringValue"]?.jsonPrimitive?.content?.let { parseLegacyProgramItem(it) }
            }
        }?.filter { it.title.isNotBlank() } ?: emptyList()

        // Documento antigo não tem a chave: lista vazia. Item sem "brotherIds"
        // também — parte sem ninguém não invalida a parte.
        val paArray = fields["programAssignments"]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
        val programAssignments = paArray?.mapNotNull { paVal ->
            val paFields = paVal.jsonObject["mapValue"]?.jsonObject?.get("fields")?.jsonObject ?: return@mapNotNull null
            val item = paFields["item"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toIntOrNull()
                ?: return@mapNotNull null
            val idsArray = paFields["brotherIds"]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
            val ids = idsArray
                ?.mapNotNull { it.jsonObject["integerValue"]?.jsonPrimitive?.content?.toLongOrNull() }
                ?: emptyList()
            ProgramAssignment(item = item, brotherIds = ids)
        } ?: emptyList()

        return Meeting(
            id = id, date = date, type = type, assignments = assignments,
            blockedBrotherIds = blocked, theme = theme, program = program,
            programAssignments = programAssignments
        )
    }

    private fun parsePublicTalk(fields: JsonObject): PublicTalk? {
        val id = fields["id"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val date = fields["date"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
        val themeNumber = fields["themeNumber"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toIntOrNull()
        val themeTitle = fields["themeTitle"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
        val speakerName = fields["speakerName"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
        val speakerCong = fields["speakerCongregation"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
        val speakerPhone = fields["speakerPhone"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
        val hospitalityId = fields["hospitalityBrotherId"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull()
        val hospitalityNotes = fields["hospitalityNotes"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
        val confirmed = fields["confirmed"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

        return PublicTalk(
            id = id,
            date = date,
            themeNumber = themeNumber,
            themeTitle = themeTitle,
            speakerName = speakerName,
            speakerCongregation = speakerCong,
            speakerPhone = speakerPhone,
            hospitalityBrotherId = hospitalityId,
            hospitalityNotes = hospitalityNotes,
            confirmed = confirmed
        )
    }

    private fun parseCleaningSchedule(fields: JsonObject): CleaningSchedule? {
        val id = fields["id"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val weekDate = fields["weekDate"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
        val groupId = fields["groupId"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull()
        val details = fields["details"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
        val completed = fields["completed"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

        return CleaningSchedule(id, weekDate, groupId, details, completed)
    }

    private fun parseFieldServiceGroup(fields: JsonObject): FieldServiceGroup? {
        val id = fields["id"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val number = fields["number"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val name = fields["name"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: "Grupo $number"
        val overseerId = fields["overseerBrotherId"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull()
        val assistantId = fields["assistantBrotherId"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull()

        return FieldServiceGroup(id, number, name, overseerId, assistantId)
    }
}
