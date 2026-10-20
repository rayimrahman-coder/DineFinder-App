package com.example.dinefinder.data

import com.example.dinefinder.Restaurant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class RestaurantCameraScanTest {
    private val origin = DiscoveryLocation(0.0, 0.0)
    private val north = fixture(1, latitude = 0.001, longitude = 0.0)
    // At the equator these independently chosen offsets give a bearing of approximately 30°.
    private val northeast = fixture(2, latitude = 0.001, longitude = 0.0005773502691896258)

    @Test
    fun radiusIncludes999And1000MetresButExcludes1001() {
        // Equatorial meridian offsets for a sphere with a 6,371 km radius.
        val records = listOf(
            fixture(1, latitude = Math.toDegrees(999.0 / 6_371_000.0), longitude = 0.0),
            fixture(2, latitude = Math.toDegrees(1000.0 / 6_371_000.0), longitude = 0.0),
            fixture(3, latitude = Math.toDegrees(1001.0 / 6_371_000.0), longitude = 0.0)
        )
        val visible = scan(records)

        assertEquals(1000.0, RestaurantCameraScan.RADIUS_METERS, 0.0)
        assertEquals(listOf(1, 2), visible.map { it.restaurant.id })
        assertEquals(999.0, visible[0].distanceMeters, 0.000001)
        assertEquals(1000.0, visible[1].distanceMeters, 0.000001)
        visible.forEach { assertPosition(it, 0.5, 0.5) }
    }

    @Test
    fun roundingToleranceDoesNotAdmitRestaurantsOneMillimetreOutsideTheRadius() {
        val justInside = fixture(1, latitude = Math.toDegrees(999.999 / 6_371_000.0), longitude = 0.0)
        val justOutside = fixture(2, latitude = Math.toDegrees(1000.001 / 6_371_000.0), longitude = 0.0)
        assertEquals(listOf(justInside), scan(listOf(justInside, justOutside)).map { it.restaurant })
    }

    @Test
    fun restaurantsBehindBesideOrOutsideTheCameraAreHidden() {
        val south = fixture(3, latitude = -0.001, longitude = 0.0)
        val east = fixture(4, latitude = 0.0, longitude = 0.001)
        val outsideView = fixture(5, latitude = 0.0005773502691896258, longitude = 0.001)

        assertEquals(listOf(north), scan(listOf(north, south, east, outsideView)).map { it.restaurant })
    }

    @Test
    fun missingOrInvalidLocationAndPoseNeverProduceMarkers() {
        val records = listOf(north)
        assertTrue(RestaurantCameraScan.visibleRestaurants(records, null, pose()).isEmpty())
        assertTrue(RestaurantCameraScan.visibleRestaurants(records, origin, null).isEmpty())
        listOf(DiscoveryLocation(Double.NaN, 0.0), DiscoveryLocation(91.0, 0.0),
            DiscoveryLocation(0.0, Double.POSITIVE_INFINITY), DiscoveryLocation(0.0, 181.0)).forEach {
            assertTrue(RestaurantCameraScan.visibleRestaurants(records, it, pose()).isEmpty())
        }
        assertEquals(listOf(north), scan(listOf(north, fixture(99, Double.NaN, 0.0)))
            .map { it.restaurant })
    }

    @Test
    fun malformedNonfiniteScaledNonorthogonalAndReflectedRotationsAreRejected() {
        val invalidRotations = listOf(
            DoubleArray(8),
            northFacing.copyOf().apply { this[0] = Double.NaN },
            northFacing.copyOf().apply { this[0] = Double.POSITIVE_INFINITY },
            northFacing.copyOf().apply { this[0] = 2.0 },
            doubleArrayOf(1.0, 1.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0),
            northFacing.copyOf().apply { this[0] = -1.0 }
        )
        invalidRotations.forEach { rotation ->
            assertTrue(scan(listOf(north), pose(rotation = rotation)).isEmpty())
        }
        assertTrue(scan(listOf(north), pose().copy(displayRotationDegrees = 45)).isEmpty())
        assertTrue(scan(listOf(north), pose().copy(declinationDegrees = Double.NaN)).isEmpty())
    }

    @Test
    fun malformedProjectionAndUnusableFieldOfViewAreRejected() {
        val invalidProjections = listOf(
            DoubleArray(15),
            projection().apply { this[0] = Double.NaN },
            projection().apply { this[5] = Double.POSITIVE_INFINITY },
            projection(scaleX = 0.0),
            projection(scaleY = -1.0),
            projection(scaleX = Double.MIN_VALUE),
            projection().apply { this[11] = 0.0 },
            doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0,
                0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        )
        invalidProjections.forEach { invalid ->
            assertTrue(scan(listOf(north), pose(projection = invalid)).isEmpty())
        }
    }

    @Test
    fun lookingNorthAndEastProjectsTheExpectedSideOfTheCamera() {
        assertPosition(scan(listOf(north)).single(), 0.5, 0.5)
        assertPosition(scan(listOf(northeast)).single(), 0.7886751345948129, 0.5)

        val east = fixture(3, latitude = 0.0, longitude = 0.001)
        val northeastSixty = fixture(4, latitude = 0.0005773502691896258, longitude = 0.001)
        assertPosition(scan(listOf(east), pose(rotation = eastFacing)).single(), 0.5, 0.5)
        assertPosition(scan(listOf(northeastSixty), pose(rotation = eastFacing)).single(),
            0.2113248654051871, 0.5)
    }

    @Test
    fun pitchingUpAndDownKeepsBothCenteredAndOffAxisMarkersInOneRow() {
        for (pitch in listOf(-60.0, -30.0, 0.0, 30.0, 60.0)) {
            val cameraPose = pose(rotation = cameraRotation(pitch = pitch))
            assertPosition(scan(listOf(north), cameraPose).single(), 0.5, 0.5)
            assertPosition(scan(listOf(northeast), cameraPose).single(), 0.7886751345948129, 0.5)
        }
    }

    @Test
    fun deviceRollDoesNotOrbitMarkersAroundTheCameraView() {
        for (roll in listOf(-180.0, -90.0, -20.0, 0.0, 20.0, 90.0, 180.0)) {
            assertPosition(scan(listOf(northeast),
                pose(rotation = cameraRotation(pitch = 30.0, roll = roll))).single(),
                0.7886751345948129, 0.5)
        }
    }

    @Test
    fun displayQuarterTurnsKeepMarkersAtTheSameHeightAndCompassSide() {
        for (rotation in listOf(0, 90, 180, 270)) {
            assertPosition(scan(listOf(northeast), pose(displayRotation = rotation)).single(),
                0.7886751345948129, 0.5)
        }

        val northeastSixty = fixture(3, latitude = 0.0005773502691896258, longitude = 0.001)
        assertPosition(scan(listOf(northeastSixty),
            pose(rotation = eastFacing, displayRotation = 90)).single(), 0.2113248654051871, 0.5)
    }

    @Test
    fun lensCalibrationControlsFrameClippingAndSupportsOffCentreProjection() {
        assertEquals(1, scan(listOf(northeast)).size)
        assertTrue(scan(listOf(northeast), pose(projection = projection(scaleX = 2.0))).isEmpty())
        assertPosition(scan(listOf(north), pose(projection =
            projection(scaleX = 2.0, scaleY = 3.0, offsetX = 0.1, offsetY = -0.2))).single(),
            0.45, 0.5)
    }

    @Test
    fun frameEdgesAreIncludedAndReturnedCoordinatesStayNormalized() {
        listOf(
            projection(offsetX = 1.0) to (0.0 to 0.5),
            projection(offsetX = -1.0) to (1.0 to 0.5),
            projection(offsetY = 1.0) to (0.5 to 0.5),
            projection(offsetY = -1.0) to (0.5 to 0.5)
        ).forEach { (projection, position) ->
            assertPosition(scan(listOf(north), pose(projection = projection)).single(),
                position.first, position.second)
        }
    }

    @Test
    fun positiveAndNegativeDeclinationCorrectTrueBearingAcrossNorth() {
        val tenDegreesEast = fixture(3, latitude = 0.001, longitude = 0.00017632698070846498)
        val tenDegreesWest = fixture(4, latitude = 0.001, longitude = -0.00017632698070846498)

        assertPosition(scan(listOf(tenDegreesEast), pose(declination = 10.0)).single(), 0.5, 0.5)
        assertPosition(scan(listOf(tenDegreesWest), pose(declination = -10.0)).single(), 0.5, 0.5)
    }

    @Test
    fun sphericalBearingWorksAcrossTheDateLineAndNearThePole() {
        val eastAcrossDateLine = fixture(3, latitude = 0.0, longitude = -179.999)
        assertPosition(RestaurantCameraScan.visibleRestaurants(listOf(eastAcrossDateLine),
            DiscoveryLocation(0.0, 179.999), pose(rotation = eastFacing)).single(), 0.5, 0.5)
        val westFacing = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        val westAcrossDateLine = fixture(4, latitude = 0.0, longitude = 179.999)
        assertPosition(RestaurantCameraScan.visibleRestaurants(listOf(westAcrossDateLine),
            DiscoveryLocation(0.0, -179.999), pose(rotation = westFacing)).single(), 0.5, 0.5)

        // Equal latitudes near the pole are separated by ~157 m; the great-circle bearing is 45°.
        val polarTarget = fixture(5, latitude = 89.999, longitude = 90.0)
        assertPosition(RestaurantCameraScan.visibleRestaurants(listOf(polarTarget),
            DiscoveryLocation(89.999, 0.0), pose(projection = projection(scaleX = 0.5))).single(),
            0.75, 0.5)
    }

    @Test
    fun allEligibleRestaurantsAreReturnedEvenBeyondFourRecords() {
        val records = (1..8).map { fixture(it, latitude = it * 0.0001, longitude = 0.0) }.reversed()
        val visible = scan(records)

        assertEquals((1..8).toList(), visible.map { it.restaurant.id })
        visible.forEach { assertTrue(it.screenX in 0.0..1.0 && it.screenY in 0.0..1.0) }
    }

    @Test
    fun markersMoveContinuouslyAcrossNorthWhenTheCameraTurns() {
        // Camera headings 350°, 0° and 10° put due north to the right, centre and left.
        val westOfNorth = doubleArrayOf(0.984807753012208, 0.0, 0.17364817766693033,
            0.17364817766693033, 0.0, -0.984807753012208, 0.0, 1.0, 0.0)
        val eastOfNorth = doubleArrayOf(0.984807753012208, 0.0, -0.17364817766693033,
            -0.17364817766693033, 0.0, -0.984807753012208, 0.0, 1.0, 0.0)

        assertPosition(scan(listOf(north), pose(rotation = westOfNorth)).single(),
            0.5881634903542325, 0.5)
        assertPosition(scan(listOf(north)).single(), 0.5, 0.5)
        assertPosition(scan(listOf(north), pose(rotation = eastOfNorth)).single(),
            0.4118365096457675, 0.5)
    }

    @Test
    fun fullCameraTurnKeepsTheCorrectRestaurantsInFrontAndOnTheCorrectSide() {
        val bearings = (0 until 360 step 45).toList()
        val targets = bearings.mapIndexed { index, bearing ->
            val radians = Math.toRadians(bearing.toDouble())
            fixture(index + 1, latitude = 0.001 * cos(radians), longitude = 0.001 * sin(radians))
        }
        for (heading in 0 until 360 step 5) {
            val visible = scan(targets, pose(
                rotation = cameraRotation(heading.toDouble(), pitch = 35.0, roll = 40.0),
                projection = projection(scaleX = 0.5)
            ))
            val expectedIds = bearings.mapIndexedNotNull { index, bearing ->
                val relative = (bearing - heading + 540) % 360 - 180
                if (abs(relative) < 63) index + 1 else null
            }.toSet()
            assertEquals("Camera heading $heading", expectedIds, visible.map { it.restaurant.id }.toSet())
            visible.forEach { match ->
                val relative = (bearings[match.restaurant.id - 1] - heading + 540) % 360 - 180
                assertPosition(match, 0.5 + 0.25 * tan(Math.toRadians(relative.toDouble())), 0.5)
            }
        }
    }

    @Test
    fun almostVerticalCameraViewsDoNotInventAnUnstableHorizontalHeading() {
        for (pitch in listOf(-90.0, -82.0, 82.0, 90.0)) {
            assertTrue(scan(listOf(north), pose(rotation = cameraRotation(pitch = pitch))).isEmpty())
        }
        for (pitch in listOf(-80.0, 80.0)) {
            assertPosition(scan(listOf(north), pose(rotation = cameraRotation(pitch = pitch))).single(), 0.5, 0.5)
        }
    }

    @Test
    fun pitchAndRollDoNotBringBehindOrHorizontallyOutOfViewRestaurantsIntoView() {
        val south = fixture(3, latitude = -0.001, longitude = 0.0)
        val outside = fixture(4, latitude = 0.0005773502691896258, longitude = 0.001)
        for (pitch in listOf(-60.0, 0.0, 60.0)) {
            for (roll in listOf(-90.0, 0.0, 90.0)) {
                assertEquals(listOf(north), scan(listOf(north, south, outside),
                    pose(rotation = cameraRotation(pitch = pitch, roll = roll))).map { it.restaurant })
            }
        }
    }

    @Test
    fun coincidentCoordinatesHaveNoInventedCameraDirection() {
        val coincident = fixture(3, latitude = 0.0, longitude = 0.0)
        val genuinelyNear = fixture(4, latitude = 0.00000001, longitude = 0.0)

        assertEquals(listOf(genuinelyNear), scan(listOf(coincident, genuinelyNear)).map { it.restaurant })
    }

    private fun scan(records: List<Restaurant>, cameraPose: CameraScanPose = pose()) =
        RestaurantCameraScan.visibleRestaurants(records, origin, cameraPose)

    private fun pose(
        rotation: DoubleArray = northFacing,
        projection: DoubleArray = projection(),
        displayRotation: Int = 0,
        declination: Double = 0.0
    ) = CameraScanPose(rotation, projection, displayRotation, declination)

    private fun projection(scaleX: Double = 1.0, scaleY: Double = 1.0,
        offsetX: Double = 0.0, offsetY: Double = 0.0) = doubleArrayOf(
        scaleX, 0.0, 0.0, 0.0,
        0.0, scaleY, 0.0, 0.0,
        offsetX, offsetY, -1.002, -1.0,
        0.0, 0.0, -0.2002, 0.0
    )

    /** An orthonormal device pose: rear optical heading, pitch above horizon, optical-axis roll. */
    private fun cameraRotation(heading: Double = 0.0, pitch: Double = 0.0, roll: Double = 0.0): DoubleArray {
        val h = Math.toRadians(heading)
        val p = Math.toRadians(pitch)
        val r = Math.toRadians(roll)
        val right = doubleArrayOf(cos(h), -sin(h), 0.0)
        val up = doubleArrayOf(-sin(h) * sin(p), -cos(h) * sin(p), cos(p))
        val back = doubleArrayOf(-sin(h) * cos(p), -cos(h) * cos(p), -sin(p))
        return doubleArrayOf(
            right[0] * cos(r) + up[0] * sin(r), -right[0] * sin(r) + up[0] * cos(r), back[0],
            right[1] * cos(r) + up[1] * sin(r), -right[1] * sin(r) + up[1] * cos(r), back[1],
            right[2] * cos(r) + up[2] * sin(r), -right[2] * sin(r) + up[2] * cos(r), back[2]
        )
    }

    private fun assertPosition(match: CameraRestaurantMatch, x: Double, y: Double) {
        assertEquals(x, match.screenX, 0.000001)
        assertEquals(y, match.screenY, 0.000001)
    }

    private fun fixture(id: Int, latitude: Double, longitude: Double) = Restaurant(
        id = id,
        name = "Test Restaurant $id",
        cuisine = "",
        halal = false,
        rating = 0f,
        hours = "",
        priceRange = "",
        address = "",
        latitude = latitude,
        longitude = longitude,
        distanceLabel = ""
    )

    // Device +X points east, +Y points up and rear-camera forward (-Z) points north.
    private val northFacing = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0)
    // Device +X points south, +Y points up and rear-camera forward (-Z) points east.
    private val eastFacing = doubleArrayOf(0.0, 0.0, -1.0, -1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
}
