package com.codewithkael.simplecall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.remote.antai.RiskBand
import com.codewithkael.simplecall.ui.theme.AntaiMuted
import com.codewithkael.simplecall.ui.theme.AntaiSurfaceVariant
import com.codewithkael.simplecall.ui.theme.RiskCaution
import com.codewithkael.simplecall.ui.theme.RiskCautionBg
import com.codewithkael.simplecall.ui.theme.RiskCritical
import com.codewithkael.simplecall.ui.theme.RiskCriticalBg
import com.codewithkael.simplecall.ui.theme.RiskSafe
import com.codewithkael.simplecall.ui.theme.RiskSafeBg

/** Visual spec for a risk band: colors, label string resource, and icon. */
private data class BandSpec(val fg: Color, val bg: Color, val labelRes: Int, val icon: Int)

private fun spec(band: RiskBand): BandSpec = when (band) {
    RiskBand.CRITICAL -> BandSpec(RiskCritical, RiskCriticalBg, R.string.risk_band_critical, R.drawable.ic_warning)
    RiskBand.CAUTION -> BandSpec(RiskCaution, RiskCautionBg, R.string.risk_band_caution, R.drawable.ic_warning)
    RiskBand.SAFE -> BandSpec(RiskSafe, RiskSafeBg, R.string.risk_band_safe, R.drawable.ic_verified)
    RiskBand.PENDING -> BandSpec(AntaiMuted, AntaiSurfaceVariant, R.string.risk_band_checking, R.drawable.ic_shield)
}

/**
 * Truecaller-style risk chip used consistently on conversation rows, message
 * bubbles and notification cards. The band itself is decided entirely by the
 * antAI server; this only renders it.
 */
@Composable
fun RiskBadge(
    band: RiskBand,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val s = spec(band)
    Row(
        modifier = modifier
            .background(s.bg, RoundedCornerShape(50))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = s.icon),
            contentDescription = null,
            tint = s.fg,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp)
        )
        Text(
            text = androidx.compose.ui.res.stringResource(id = s.labelRes),
            color = s.fg,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** Whether a band is worth surfacing as a chip in dense lists (hide plain SAFE). */
fun RiskBand.isNoteworthy(): Boolean = this != RiskBand.SAFE
