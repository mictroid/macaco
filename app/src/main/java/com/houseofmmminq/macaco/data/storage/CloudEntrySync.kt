package com.houseofmmminq.macaco.data.storage

import com.houseofmmminq.macaco.data.auth.AuthRepository
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CloudEntrySync(
    private val authRepository: AuthRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val firestore = FirebaseFirestore.getInstance()

    private val _entries = MutableStateFlow<List<TravelEntry>>(emptyList())
    val entries: StateFlow<List<TravelEntry>> = _entries.asStateFlow()

    // One-shot, user-facing error messages for the UI to surface (e.g. a snackbar). Buffered so an
    // error emitted during a screen transition isn't lost before a collector attaches.
    private val _errors = Channel<String>(Channel.BUFFERED)
    val errors: Flow<String> = _errors.receiveAsFlow()

    private var listenerRegistration: ListenerRegistration? = null

    init {
        scope.launch {
            authRepository.currentUser.collect { user ->
                listenerRegistration?.remove()
                listenerRegistration = null
                if (user == null) {
                    _entries.value = emptyList()
                } else {
                    startListening(user.uid)
                }
            }
        }
    }

    private fun startListening(uid: String) {
        listenerRegistration = firestore
            .collection("users").document(uid).collection("entries")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Going offline fires UNAVAILABLE; entries are still served from cache, so don't
                    // alarm the user — only surface non-connectivity errors.
                    val isOffline = (error as? FirebaseFirestoreException)?.code ==
                        FirebaseFirestoreException.Code.UNAVAILABLE
                    if (!isOffline) {
                        _errors.trySend("Couldn't sync your entries. Changes are saved and will catch up when you're back online.")
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                _entries.value = snapshot.documents.mapNotNull { doc ->
                    runCatching {
                        TravelEntry(
                            id = doc.getString("id") ?: doc.id,
                            title = doc.getString("title") ?: return@runCatching null,
                            location = doc.getString("location") ?: "",
                            dateMillis = doc.getLong("dateMillis") ?: 0L,
                            description = doc.getString("description") ?: "",
                            mood = doc.getString("mood") ?: "",
                            // Photos stored as local URIs — visible on the device they were added from
                            photoUris = (doc.get("photoUris") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            tags = (doc.get("tags") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            createdAt = doc.getLong("createdAt") ?: 0L,
                            driveFileIds = (doc.get("driveFileIds") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            tripName = doc.getString("tripName"),
                            videoUris = (doc.get("videoUris") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            videoFileIds = (doc.get("videoFileIds") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            mediaOrder = (doc.get("mediaOrder") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList()
                        )
                    }.getOrNull()
                }
            }
    }

    // No .await() on the writes below: Firestore queues them to its local cache and syncs when
    // connectivity returns, so saving/deleting works offline. The failure listener fires only on a
    // genuine server rejection (e.g. permission), not a connectivity gap, so offline writes don't
    // surface a false error.
    suspend fun save(entry: TravelEntry) {
        val uid = authRepository.currentUser.value?.uid ?: return
        firestore.collection("users").document(uid)
            .collection("entries").document(entry.id)
            .set(entry.toMap())
            .addOnFailureListener { _errors.trySend("Couldn't save your entry. Please try again.") }
    }

    suspend fun delete(id: String) {
        val uid = authRepository.currentUser.value?.uid ?: return
        firestore.collection("users").document(uid)
            .collection("entries").document(id)
            .delete()
            .addOnFailureListener { _errors.trySend("Couldn't delete the entry. Please try again.") }
    }

    private fun TravelEntry.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "location" to location,
        "dateMillis" to dateMillis,
        "description" to description,
        "mood" to mood,
        "photoUris" to photoUris,
        "tags" to tags,
        "createdAt" to createdAt,
        "driveFileIds" to driveFileIds,
        "tripName" to tripName,
        "videoUris" to videoUris,
        "videoFileIds" to videoFileIds,
        "mediaOrder" to mediaOrder
    )
}
