package com.radiofides.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.radiofides.R

@Composable
fun SocialButtonsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        SocialCircleButton(R.drawable.iconfacebook, Color(0xFF1877F2)) { /* Link FB */ }
        Spacer(modifier = Modifier.width(20.dp))
        SocialCircleButton(R.drawable.icontiktok, Color(0xFF25D366)) { /* Link WA */ }
        Spacer(modifier = Modifier.width(20.dp))
        SocialCircleButton(R.drawable.icontwitter, Color(0xFF000000)) { /* Link X */ }
    }
}

@Composable
fun SocialCircleButton(iconRes: Int, color: Color, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
    }
}