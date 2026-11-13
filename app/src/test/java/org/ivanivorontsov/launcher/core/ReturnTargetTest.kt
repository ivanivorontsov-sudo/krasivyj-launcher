package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReturnTargetTest {
    @Test fun openFolderUsesTheIconInsideIt() {
        assertEquals(
            ReturnSpot.INNER,
            chooseReturnSpot(launchedFromFolder = true, folderOpen = true, hasInner = true, hasLaunchBounds = true, hasGridFolder = true, hasHomeIcon = true),
        )
    }

    @Test fun openFolderFallsBackToTheLaunchCookieUntilTheInnerIconIsLaidOut() {
        assertEquals(
            ReturnSpot.LAUNCH_BOUNDS,
            chooseReturnSpot(launchedFromFolder = true, folderOpen = true, hasInner = false, hasLaunchBounds = true, hasGridFolder = true, hasHomeIcon = false),
        )
    }

    @Test fun closedFolderLandsOnTheGridFolder() {
        assertEquals(
            ReturnSpot.GRID_FOLDER,
            chooseReturnSpot(launchedFromFolder = true, folderOpen = false, hasInner = false, hasLaunchBounds = true, hasGridFolder = true, hasHomeIcon = false),
        )
    }

    @Test fun homeLaunchUsesTheHomeIcon() {
        assertEquals(
            ReturnSpot.HOME_ICON,
            chooseReturnSpot(launchedFromFolder = false, folderOpen = false, hasInner = false, hasLaunchBounds = true, hasGridFolder = false, hasHomeIcon = true),
        )
    }
}
