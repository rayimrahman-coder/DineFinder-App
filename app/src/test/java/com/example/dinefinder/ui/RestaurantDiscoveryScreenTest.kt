package com.example.dinefinder.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.dinefinder.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RestaurantDiscoveryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val origin = GeoPoint(5.280285448386224,115.17640486697412)
    private val cafe = Restaurant("cafe", "Cafe UMSKAL", "Food court", "", "",origin,0.0,1,"",0L,isHalal=true)
    private val anjung = cafe.copy(id="anjung",name="Anjung Selera Sungai Pagar",location=GeoPoint(5.28381416215476,115.17329350236913))
    private fun screen(restaurants: List<Restaurant> = listOf(cafe,anjung), onSelect: (Restaurant)->Unit = {}, route: RoutePlan? = null) {
        compose.setContent { MaterialTheme {
            ScanScreen(origin,340.0,restaurants,route,if(route==null)null else anjung,true,false,null,null,null,false,
                onSelect,{}, {}, {}, {}, {},0.0)
        } }
    }
    private fun state(value:String) = compose.onNodeWithTag("restaurant_discovery_sheet")
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,value))
    private fun preview(name:String) {
        compose.waitForIdle()
        val view=compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file=File("build/reports/previews/discovery-$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun approvedHeaderHasNoCameraListTabsAndSheetHasFullNames() {
        screen()
        compose.onNodeWithText("Discover nearby").assertIsDisplayed()
        compose.onNodeWithText("Camera",substring=false).assertDoesNotExist()
        compose.onNodeWithText("List",substring=false).assertDoesNotExist()
        compose.onNodeWithContentDescription("Scan checklist").assertIsDisplayed()
        state("Half")
        compose.onAllNodesWithText(anjung.name).filter(hasAnyAncestor(hasTestTag("restaurant_discovery_sheet"))).onFirst().assertIsDisplayed()
        preview("half")
    }
    @Test fun chevronCanExpandMinimiseAndRestoreWithoutDragging() {
        screen()
        compose.onNodeWithContentDescription("Expand restaurants").performClick()
        state("Expanded");preview("expanded")
        compose.onNodeWithContentDescription("Collapse restaurants").performClick()
        state("Collapsed");preview("collapsed")
        compose.onNodeWithText("2 nearby restaurants").assertIsDisplayed()
        compose.onNodeWithTag("nearby_restaurant_list").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expand restaurants").performClick()
        state("Half")
    }
    @Test fun draggingHeaderCanExpandAndCollapsePanel() {
        screen()
        compose.onNodeWithTag("restaurant_sheet_handle").performTouchInput {
            swipe(start=center,end=Offset(center.x,-450f),durationMillis=500)
        }
        state("Expanded")
        compose.onNodeWithTag("restaurant_sheet_handle").performTouchInput {
            swipe(start=center,end=Offset(center.x,700f),durationMillis=600)
        }
        state("Collapsed")
    }
    @Test fun expandedListCanScrollToOtherRestaurantsAndOpenTheirProfiles() {
        var selected:String?=null
        val records=(0..19).map { cafe.copy(id="r$it",name="Campus Restaurant $it") }
        screen(records,{selected=it.id})
        compose.onNodeWithContentDescription("Expand restaurants").performClick()
        compose.onNodeWithTag("nearby_restaurant_list").performScrollToNode(hasText("Campus Restaurant 19"))
        compose.onAllNodesWithText("Campus Restaurant 19").filter(hasAnyAncestor(hasTestTag("restaurant_discovery_sheet"))).onFirst().performClick()
        compose.runOnIdle { assertEquals("r19",selected) }
        state("Expanded")
    }
    @Test fun swipingTheListExpandsPanelBeforeScrollingItsRows() {
        screen((0..19).map { cafe.copy(id="r$it",name="Campus Restaurant $it") })
        compose.onNodeWithTag("nearby_restaurant_list").performTouchInput { swipeUp() }
        state("Expanded")
        compose.onNodeWithTag("nearby_restaurant_list").performScrollToNode(hasText("Campus Restaurant 19"))
        compose.onNodeWithTag("nearby_restaurant_list").performTouchInput {
            swipe(start=center,end=Offset(center.x,center.y + 120f),durationMillis=600)
        }
        state("Expanded")
    }

    @Test fun routeGuidanceAndWrittenDirectionsRemainAvailable() {
        screen(route=DemoData.route(origin,anjung.location))
        compose.onNodeWithText("Walking navigation").assertIsDisplayed()
        compose.onNodeWithTag("restaurant_discovery_sheet").assertDoesNotExist()
        compose.onNodeWithText("Directions").performClick()
        compose.onNodeWithText("Walking directions").assertIsDisplayed()
    }
    @Test
    @Config(sdk=[28],qualifiers="w320dp-h480dp",fontScale=2f)
    fun smallScreenWithLargeTextKeepsSheetControlsAndProfilesReachable() {
        screen()
        compose.onNodeWithContentDescription("Expand restaurants").assertIsDisplayed().performClick()
        state("Expanded")
        compose.onNodeWithTag("nearby_restaurant_list").performScrollToNode(hasText(anjung.name))
        compose.onAllNodesWithText(anjung.name).filter(hasAnyAncestor(hasTestTag("restaurant_discovery_sheet"))).onFirst().assertIsDisplayed()
    }
}
