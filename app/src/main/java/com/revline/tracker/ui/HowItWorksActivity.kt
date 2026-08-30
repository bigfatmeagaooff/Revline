package com.revline.tracker.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.revline.tracker.R
import com.revline.tracker.databinding.ActivityHowItWorksBinding
import com.revline.tracker.util.EdgeToEdge

/** Permanent "how the app works" reference, reachable from Profile. */
class HowItWorksActivity : AppCompatActivity() {

    private val sections = listOf(
        R.string.hiw_record_t to R.string.hiw_record_b,
        R.string.hiw_slip_t to R.string.hiw_slip_b,
        R.string.hiw_auto_t to R.string.hiw_auto_b,
        R.string.hiw_account_t to R.string.hiw_account_b,
        R.string.hiw_social_t to R.string.hiw_social_b,
        R.string.hiw_photo_t to R.string.hiw_photo_b,
        R.string.hiw_updates_t to R.string.hiw_updates_b,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityHowItWorksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(binding.root)
        binding.backButton.setOnClickListener { finish() }

        val section = ResourcesCompat.getFont(this, R.font.rl_display)
        val body = ResourcesCompat.getFont(this, R.font.inter_regular)

        sections.forEachIndexed { i, (title, text) ->
            binding.sections.addView(TextView(this).apply {
                setText(title)
                typeface = section
                isAllCaps = true
                letterSpacing = 0.08f
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@HowItWorksActivity, R.color.redline))
                layoutParams = rowParams(if (i == 0) 12 else 28)
            })
            binding.sections.addView(TextView(this).apply {
                setText(text)
                typeface = body
                textSize = 15f
                setLineSpacing(dp(4).toFloat(), 1f)
                setTextColor(ContextCompat.getColor(this@HowItWorksActivity, R.color.print_dim))
                gravity = Gravity.START
                layoutParams = rowParams(8)
            })
        }
    }

    private fun rowParams(topDp: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(topDp) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
