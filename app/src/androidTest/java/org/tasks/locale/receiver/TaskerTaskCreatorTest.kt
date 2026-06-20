package org.tasks.locale.receiver

import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.caldav.GeoUtils.toLikeString
import org.tasks.data.dao.LocationDao
import org.tasks.injection.InjectingTestCase
import org.tasks.locale.bundle.TaskCreationBundle
import javax.inject.Inject

@HiltAndroidTest
class TaskerTaskCreatorTest : InjectingTestCase() {
    @Inject lateinit var taskerTaskCreator: TaskerTaskCreator
    @Inject lateinit var locationDao: LocationDao

    @Test
    fun createArrivalGeofenceFromCoordinates() = runBlocking {
        taskerTaskCreator.handle(bundle(
            latitude = "12.345678",
            longitude = "98.765432",
            arrival = "true",
        ))

        val place = locationDao.findPlace(12.345678.toLikeString(), 98.765432.toLikeString())
        assertNotNull(place)
        assertEquals(12.345678, place?.latitude)
        assertEquals(98.765432, place?.longitude)

        val geofence = locationDao.getGeofencesByPlace(place!!.uid!!)
        assertNotNull(geofence)
        assertTrue(geofence!!.arrival)
        assertFalse(geofence.departure)
    }

    @Test
    fun createDepartureGeofenceFromCoordinates() = runBlocking {
        taskerTaskCreator.handle(bundle(
            latitude = "-33.8688",
            longitude = "151.2093",
            departure = "true",
        ))

        val place = locationDao.findPlace((-33.8688).toLikeString(), 151.2093.toLikeString())
        val geofence = locationDao.getGeofencesByPlace(place!!.uid!!)
        assertFalse(geofence!!.arrival)
        assertTrue(geofence.departure)
    }

    @Test
    fun customRadiusIsApplied() = runBlocking {
        taskerTaskCreator.handle(bundle(
            latitude = "11.111111",
            longitude = "22.222222",
            radius = "500",
            arrival = "true",
        ))

        val place = locationDao.findPlace(11.111111.toLikeString(), 22.222222.toLikeString())
        assertEquals(500, place?.radius)
    }

    @Test
    fun noCoordinatesCreatesNoNewPlace() = runBlocking {
        val before = locationDao.getPlaces().size

        taskerTaskCreator.handle(bundle(title = "no location"))

        assertEquals(before, locationDao.getPlaces().size)
    }

    private fun bundle(
        title: String = "task",
        latitude: String? = null,
        longitude: String? = null,
        radius: String? = null,
        arrival: String? = null,
        departure: String? = null,
    ) = TaskCreationBundle().apply {
        setTitle(title)
        latitude?.let { setLatitude(it) }
        longitude?.let { setLongitude(it) }
        radius?.let { setRadius(it) }
        arrival?.let { setArrival(it) }
        departure?.let { setDeparture(it) }
    }
}
