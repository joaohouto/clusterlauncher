package com.joaohouto.clusterlauncher.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.data.model.AppItem
import com.joaohouto.clusterlauncher.ui.theme.NeedleRed
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary

private val ItemShape = RoundedCornerShape(20.dp)
private val IconShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGridItem(
    app: AppItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(ItemShape)
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardBorder, ItemShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = NeedleRed),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier
                    .size(92.dp)
                    .clip(IconShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(IconShape)
                    .background(SurfaceCardBorder)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = app.label,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
