package com.revline.tracker.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.revline.tracker.LoginActivity
import com.revline.tracker.MainActivity
import com.revline.tracker.R
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.databinding.ActivityOnboardingBinding
import com.revline.tracker.databinding.ItemOnboardingPageBinding
import com.revline.tracker.util.EdgeToEdge
import android.content.Intent

/**
 * First-run walkthrough. Shown once (tracked in [SEEN_PREF]); after the last page or
 * Skip it just finishes and the caller carries on to wherever it was headed.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private data class Page(val icon: Int, val title: Int, val body: Int)

    private val pages = listOf(
        Page(R.drawable.ic_directions_car, R.string.onb1_title, R.string.onb1_body),
        Page(R.drawable.ic_play, R.string.onb2_title, R.string.onb2_body),
        Page(R.drawable.ic_leaderboard, R.string.onb3_title, R.string.onb3_body),
        Page(R.drawable.ic_bell, R.string.onb4_title, R.string.onb4_body),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(binding.root)
        markSeen(this)

        binding.pager.adapter = PageAdapter()
        buildDots()

        binding.skip.setOnClickListener { continueOn() }
        binding.nextButton.setOnClickListener {
            val next = binding.pager.currentItem + 1
            if (next < pages.size) binding.pager.currentItem = next else continueOn()
        }

        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                val last = position == pages.size - 1
                binding.nextButton.setText(if (last) R.string.onboarding_start else R.string.onboarding_next)
                binding.skip.visibility = if (last) View.INVISIBLE else View.VISIBLE
            }
        })
        updateDots(0)
    }

    private fun buildDots() {
        binding.dots.removeAllViews()
        val size = dp(7)
        val gap = dp(5)
        repeat(pages.size) {
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = gap; marginEnd = gap }
            binding.dots.addView(v)
        }
    }

    private fun updateDots(active: Int) {
        for (i in 0 until binding.dots.childCount) {
            val on = i == active
            val d = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@OnboardingActivity, if (on) R.color.redline else R.color.print_faint))
            }
            binding.dots.getChildAt(i).background = d
        }
    }

    private fun continueOn() {
        // This is the only screen in the stack (launched from Splash on first run);
        // route onward to wherever a cold start would have gone.
        val loggedIn = SyncRepository.getInstance(this).isLoggedIn
        startActivity(Intent(this, if (loggedIn) MainActivity::class.java else LoginActivity::class.java))
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.VH>() {
        inner class VH(val b: ItemOnboardingPageBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = pages.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = pages[position]
            holder.b.pageIcon.setImageResource(p.icon)
            holder.b.pageTitle.setText(p.title)
            holder.b.pageBody.setText(p.body)
        }
    }

    companion object {
        private const val PREFS = "revline_onboarding"
        private const val SEEN_PREF = "seen"

        fun shouldShow(context: Context): Boolean =
            !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SEEN_PREF, false)

        private fun markSeen(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(SEEN_PREF, true).apply()
        }
    }
}
