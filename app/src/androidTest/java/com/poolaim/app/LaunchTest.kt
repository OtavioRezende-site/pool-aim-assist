package com.poolaim.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
class LaunchTest {
 @get:Rule val compose = createAndroidComposeRule<MainActivity>()
 @Test fun homeAndSimulatorOpen() {
  compose.onNodeWithText("Iniciar simulador").assertIsDisplayed().performClick()
  compose.onNodeWithText("Reiniciar").assertIsDisplayed().performClick()
  compose.onNodeWithText("Voltar ao início").performClick()
  compose.onNodeWithText("Calibrar mesa").performClick()
  compose.onNodeWithText("Calibração manual do simulador").assertIsDisplayed()
 }
}
