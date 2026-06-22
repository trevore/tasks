package org.tasks.location

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.entity.Task
import org.tasks.injection.InjectingTestCase
import javax.inject.Inject

@HiltAndroidTest
class GeoMarkerPromoteWorkerTest : InjectingTestCase() {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var locationDao: LocationDao

    @Test
    fun promotesMarkerToGeofenceAndStripsNote() = runBlocking {
        val id = taskDao.createNew(
            Task(
                notes = "Pick up prescription\n" +
                    "@gomu-geo v1 lat=37.4220 lng=-122.0841 r=150 arrive=on place=\"Walgreens\""
            )
        )

        runWorker()

        val geofences = locationDao.getGeofencesForTask(id)
        assertEquals(1, geofences.size)
        assertEquals(true, geofences.first().isArrival)
        assertEquals(false, geofences.first().isDeparture)
        assertEquals("Pick up prescription", taskDao.fetch(id)?.notes)
    }

    @Test
    fun runningTwiceCreatesExactlyOneGeofence() = runBlocking {
        val id = taskDao.createNew(Task(notes = "@gomu-geo v1 lat=37.4220 lng=-122.0841"))

        runWorker()
        runWorker()

        assertEquals(1, locationDao.getGeofencesForTask(id).size)
    }

    private fun runWorker() {
        TestListenableWorkerBuilder<GeoMarkerPromoteWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
            .startWork()
            .get()
    }
}
