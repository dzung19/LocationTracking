package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OrangeTreeCanvas(
    level: Int,
    streak: Int,
    modifier: Modifier = Modifier
) {
    // Colors for the pixel art
    val trunkColor = Color(0xFF5D4037) // Brown
    val leafColor = Color(0xFF2E7D32)  // Green
    val soilColor = Color(0xFF37474F)  // Slate/Soil
    val flowerColor = Color(0xFFFFFFFF)// White
    val flowerCenterColor = Color(0xFFFFEB3B) // Yellow
    val orangeColor = Color(0xFFFF9800) // Orange

    // Define grids as 16x16 matrices
    val level1Grid = listOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        ".......LL.......",
        ".....L.LL.L.....",
        "......LLL.......",
        ".......T........",
        ".......T........",
        "....SSSSSSSS....",
        "...SSSSSSSSSS...",
        "................"
    )

    val level2Grid = listOf(
        "................",
        "................",
        "................",
        "................",
        "......LLLLL.....",
        "....LLLLLLLLL...",
        "....LLLLLLLLL...",
        ".....LLLLLLL....",
        "......LLLLL.....",
        ".......T.T......",
        ".......T.T......",
        "........T.......",
        "........T.......",
        "....SSSSSSSS....",
        "...SSSSSSSSSS...",
        "................"
    )

    val level3Grid = listOf(
        "................",
        "......LLLLLL....",
        "....LLLLLLLLLL..",
        "...LLLLLLLLLLLL.",
        "...LLLLLLLLLLLL.",
        "....LLLLLLLLLL..",
        "......LLLLLL....",
        ".......T..T.....",
        ".......T.T......",
        "........T.......",
        "........T.......",
        "........T.......",
        "........T.......",
        "....SSSSSSSS....",
        "...SSSSSSSSSS...",
        "................"
    )

    val level4Grid = listOf(
        ".....LLLLLLLL...",
        "...LLLLLLLLLLLL.",
        "..LLFLLLLLLFLLLL",
        ".LLLLLLLLLLLLLLL",
        ".LLLFLLLLLLFLLLL",
        "..LLLLLLLLLLLLL.",
        "...LLLLLLLLLLL..",
        ".....T.LLLL.T...",
        ".....T..TT..T...",
        "......T.TT.T....",
        ".......TTT......",
        "........T.......",
        "........T.......",
        "....SSSSSSSS....",
        "...SSSSSSSSSS...",
        "................"
    )

    // Base grid for Level 5 (Fruiting Tree)
    val level5Base = listOf(
        "....LLLLLLLLLL..",
        "..LLLLLLLLLLLLLL",
        ".LLLLLLLLLLLLLLL",
        "LLLLLLLLLLLLLLLL",
        "LLLLLLLLLLLLLLLL",
        ".LLLLLLLLLLLLLL.",
        "..LLLLLLLLLLLL..",
        "....T.LLLL.T....",
        "....T..TT..T....",
        ".....T.TT.T.....",
        "......TTT.......",
        ".......T........",
        ".......T........",
        "....SSSSSSSS....",
        "...SSSSSSSSSS...",
        "................"
    )

    // Select the grid based on level
    val rawGrid = when (level) {
        1 -> level1Grid
        2 -> level2Grid
        3 -> level3Grid
        4 -> level4Grid
        else -> level5Base
    }

    // Convert to mutable char matrix for dynamic modification
    val grid = rawGrid.map { it.toCharArray() }

    // If Level 5, add oranges dynamically based on streak
    if (level >= 5 && streak > 0) {
        // Define coordinates (row, col) for the top-left of up to 8 oranges (2x2 pixel size each)
        val orangeSlots = listOf(
            Pair(2, 3),   // Left top
            Pair(2, 11),  // Right top
            Pair(4, 5),   // Left mid
            Pair(4, 9),   // Right mid
            Pair(1, 7),   // Center top
            Pair(3, 7),   // Center mid
            Pair(5, 2),   // Far left
            Pair(5, 12)   // Far right
        )

        val orangesToDraw = minOf(streak, orangeSlots.size)
        for (i in 0 until orangesToDraw) {
            val (row, col) = orangeSlots[i]
            // Draw a 2x2 block of oranges
            if (row < 15 && col < 15) {
                grid[row][col] = 'O'
                grid[row][col + 1] = 'O'
                grid[row + 1][col] = 'O'
                grid[row + 1][col + 1] = 'O'
            }
        }
    }

    Canvas(modifier = modifier.size(120.dp)) {
        val gridSize = 16
        val pixelWidth = size.width / gridSize
        val pixelHeight = size.height / gridSize

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val char = grid[row][col]
                val color = when (char) {
                    'T' -> trunkColor
                    'L' -> leafColor
                    'S' -> soilColor
                    'F' -> flowerColor
                    'Y' -> flowerCenterColor
                    'O' -> orangeColor
                    else -> Color.Transparent
                }

                if (color != Color.Transparent) {
                    drawRect(
                        color = color,
                        topLeft = Offset(col * pixelWidth, row * pixelHeight),
                        size = Size(pixelWidth, pixelHeight)
                    )
                }
            }
        }
    }
}
