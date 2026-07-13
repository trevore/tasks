package org.tasks.location

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tasks.analytics.Firebase
import org.tasks.caldav.GeoUtils.toLikeString
import org.tasks.data.TaskSaver
import org.tasks.data.createGeofence
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Place
import org.tasks.data.entity.Task
import org.tasks.injection.BaseWorker
import org.tasks.preferences.Preferences
import timber.log.Timber

/**
 * Materializes device-local geofences from `@gomu-geo` markers that arrive via
 * Google Tasks sync (the gomu Cowork bridge). Geofences cannot be created in the
 * cloud, so this on-device worker finds tasks carrying a marker, builds the Place
 * and Geofence the same way the in-app map picker and Tasker action do, strips the
 * marker line so the cleaned note re-syncs up, and registers the fences. Personal
 * fork only; not part of the upstream Tasker contribution.
 */
@HiltWorker
class GeoMarkerPromoteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    firebase: Firebase,
    private val taskDao: TaskDao,
    private val taskSaver: TaskSaver,
    private val locationDao: LocationDao,
    private val preferences: Preferences,
) : BaseWorker(context, workerParams, firebase) {

    override suspend fun run(): Result {
        // Single-flight: the sync-triggered one-time worker and the periodic worker
        // live under different unique WorkManager names, so WorkManager can run them
        // concurrently — and the geofence guard in promote() is check-then-insert.
        // Serialize the whole pass (task list fetched under the lock) so a race can
        // never mint a duplicate geofence.
        PROMOTE_MUTEX.withLock {
            for (task in taskDao.getGeoMarkerTasks()) {
                try {
                    promote(task)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to promote geo marker for task %d", task.id)
                }
            }
        }
        return Result.success()
    }

    private suspend fun promote(task: Task) {
        // Primary idempotency guard: a task that already has a geofence must never
        // create a second one. Still strip any lingering marker line so it cannot
        // re-fire on a future pass, then skip creation.
        if (locationDao.getGeofencesForTask(task.id).isNotEmpty()) {
            stripAndSave(task)
            return
        }
        val marker = parseGeoMarker(task.notes)
        if (marker == null) {
            // The DAO query matches any "@gomu-geo" substring. A marker LINE that
            // failed validation is machine-owned and will never arm — strip it
            // (with a breadcrumb) instead of re-parsing it every period. A mere
            // mid-line mention has no marker line, so stripAndSave is a no-op.
            if (stripGeoMarker(task.notes) != task.notes) {
                Timber.w("Stripping invalid @gomu-geo marker from task %d", task.id)
            }
            stripAndSave(task)
            return
        }
        var place = Place(name = marker.place, latitude = marker.lat, longitude = marker.lng)
        marker.radius?.let { place = place.copy(radius = it) }
        // An existing place at these coords wins, including its radius (a marker
        // r= is ignored then) — same reuse the in-app map picker and the Tasker
        // action apply.
        place = locationDao
            .findPlace(place.latitude.toLikeString(), place.longitude.toLikeString())
            ?: place.copy(id = locationDao.insert(place))
        var geofence = createGeofence(place.uid, preferences).copy(task = task.id)
        if (marker.hasFlags) {
            geofence = geofence.copy(
                isArrival = marker.arrive ?: false,
                isDeparture = marker.depart ?: false,
            )
        }
        locationDao.insert(geofence)
        stripAndSave(task)
        RegisterGeofencesWork.enqueue(context)
    }

    private suspend fun stripAndSave(task: Task) {
        val cleaned = stripGeoMarker(task.notes)
        if (cleaned != task.notes) {
            // Snapshot before mutating so TaskSaver can diff old vs new and mark
            // the cleaned note dirty — that dirty flag is what re-syncs the strip
            // back up. (15.7.3 made `original` a required arg on save().)
            val original = task.copy()
            task.notes = cleaned
            taskSaver.save(task, original)
        }
    }

    companion object {
        private const val WORK_NAME = "promote_geo_markers"
        private val PROMOTE_MUTEX = Mutex()

        fun enqueue(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<GeoMarkerPromoteWorker>().build()
                )
        }
    }
}
