package org.ivanivorontsov.launcher.core

/**
 * Where a closing app should fly. An app opened from inside a folder must land on that
 * inner icon while the folder stays open; if the folder is already gone, it lands on the
 * folder icon on the grid instead of a loose home slot.
 */
enum class ReturnSpot { INNER, LAUNCH_BOUNDS, GRID_FOLDER, HOME_ICON }

fun chooseReturnSpot(
    launchedFromFolder: Boolean,
    folderOpen: Boolean,
    hasInner: Boolean,
    hasLaunchBounds: Boolean,
    hasGridFolder: Boolean,
    hasHomeIcon: Boolean,
): ReturnSpot? = when {
    launchedFromFolder && folderOpen && hasInner -> ReturnSpot.INNER
    launchedFromFolder && folderOpen && hasLaunchBounds -> ReturnSpot.LAUNCH_BOUNDS
    launchedFromFolder && hasGridFolder -> ReturnSpot.GRID_FOLDER
    launchedFromFolder && hasLaunchBounds -> ReturnSpot.LAUNCH_BOUNDS
    hasHomeIcon -> ReturnSpot.HOME_ICON
    hasLaunchBounds -> ReturnSpot.LAUNCH_BOUNDS
    hasGridFolder -> ReturnSpot.GRID_FOLDER
    else -> null
}
