package org.tasks.locale.receiver

import android.content.Context
import com.todoroo.astrid.service.TaskCreator
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.Strings.isNullOrEmpty
import org.tasks.analytics.Firebase
import org.tasks.caldav.GeoUtils.toLikeString
import org.tasks.data.TaskSaver
import org.tasks.data.createDueDate
import org.tasks.data.createGeofence
import org.tasks.data.dao.AlarmDao
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.TagDao
import org.tasks.data.entity.Place
import org.tasks.data.entity.Task
import org.tasks.data.getDefaultAlarms
import org.tasks.location.RegisterGeofencesWork
import org.tasks.locale.bundle.TaskCreationBundle
import org.tasks.preferences.Preferences
import org.tasks.time.DateTime
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class TaskerTaskCreator @Inject internal constructor(
    private val taskCreator: TaskCreator,
    private val taskSaver: TaskSaver,
    private val firebase: Firebase,
    private val alarmDao: AlarmDao,
    private val preferences: Preferences,
    private val tagDao: TagDao,
    private val locationDao: LocationDao,
    @ApplicationContext private val context: Context,
) {
    suspend fun handle(bundle: TaskCreationBundle) {
        val task = taskCreator.basicQuickAddTask(bundle.title)
        val dueDateString = bundle.dueDate
        if (!isNullOrEmpty(dueDateString)) {
            try {
                val dueDate = LocalDate.parse(dueDateString, dateFormatter)
                val dt = DateTime(dueDate.year, dueDate.monthValue, dueDate.dayOfMonth)
                task.dueDate = createDueDate(Task.URGENCY_SPECIFIC_DAY, dt.millis)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        val dueTimeString = bundle.dueTime
        if (!isNullOrEmpty(dueTimeString)) {
            try {
                val dueTime = LocalTime.parse(dueTimeString, timeFormatter)
                task.dueDate = createDueDate(
                        Task.URGENCY_SPECIFIC_DAY_TIME,
                        DateTime(if (task.hasDueDate()) task.dueDate else currentTimeMillis())
                                .withHourOfDay(dueTime.hour)
                                .withMinuteOfHour(dueTime.minute)
                                .millis)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        val priorityString = bundle.priority
        if (!isNullOrEmpty(priorityString)) {
            try {
                val priority = priorityString.toInt()
                task.priority = max(Task.Priority.HIGH, min(Task.Priority.NONE, priority))
            } catch (e: NumberFormatException) {
                Timber.e(e)
            }
        }
        task.notes = bundle.description
        taskSaver.save(task, null)
        alarmDao.insert(task.getDefaultAlarms(preferences.isDefaultDueTimeEnabled()))
        tagDao.insert(task, task.tags)
        firebase.addTask("tasker")
        val latitudeString = bundle.latitude
        val longitudeString = bundle.longitude
        if (!isNullOrEmpty(latitudeString) && !isNullOrEmpty(longitudeString)) {
            try {
                val latitude = latitudeString.toDouble()
                val longitude = longitudeString.toDouble()
                // NaN/Infinity slip past a bare range check (every comparison with
                // NaN is false), so reject non-finite coords explicitly before the
                // bounds test — Tasker variables are free text.
                if (!latitude.isFinite() || !longitude.isFinite() ||
                        latitude < -90.0 || latitude > 90.0 ||
                        longitude < -180.0 || longitude > 180.0) {
                    Timber.w("Ignoring invalid coordinates lat=%s lng=%s", latitudeString, longitudeString)
                    return
                }
                var place = Place(
                        name = bundle.placeName,
                        latitude = latitude,
                        longitude = longitude)
                val radiusString = bundle.radius
                if (!isNullOrEmpty(radiusString)) {
                    try {
                        // A non-positive radius is rejected by the geofence
                        // registrar; drop it so the place falls back to the
                        // app default rather than failing to arm.
                        radiusString.toInt().takeIf { it > 0 }
                                ?.let { place = place.copy(radius = it) }
                    } catch (e: NumberFormatException) {
                        Timber.e(e)
                    }
                }
                // An existing place at these coords wins, including its radius —
                // same reuse the in-app map picker applies.
                place = locationDao
                        .findPlace(place.latitude.toLikeString(), place.longitude.toLikeString())
                        ?: place.copy(id = locationDao.insert(place))
                var geofence = createGeofence(place.uid, preferences).copy(task = task.id)
                if (!isNullOrEmpty(bundle.arrival) || !isNullOrEmpty(bundle.departure)) {
                    geofence = geofence.copy(
                            isArrival = parseFlag(bundle.arrival) ?: false,
                            isDeparture = parseFlag(bundle.departure) ?: false)
                }
                locationDao.insert(geofence)
                RegisterGeofencesWork.enqueue(context)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private fun parseFlag(value: String?): Boolean? =
            if (isNullOrEmpty(value)) null else value.equals("true", ignoreCase = true) || value == "1"

    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val timeFormatter = DateTimeFormatter.ISO_LOCAL_TIME
    }
}
