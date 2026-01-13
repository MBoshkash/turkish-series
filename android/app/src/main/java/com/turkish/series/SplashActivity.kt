package com.turkish.series

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.turkish.series.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start animations
        startAnimations()
    }

    private fun startAnimations() {
        // Initial state - all elements invisible and scaled down
        binding.logoCircle.alpha = 0f
        binding.logoCircle.scaleX = 0f
        binding.logoCircle.scaleY = 0f

        binding.playButton.alpha = 0f
        binding.playButton.scaleX = 0f
        binding.playButton.scaleY = 0f

        binding.crescentTop.alpha = 0f
        binding.crescentTop.translationX = -100f
        binding.crescentTop.translationY = -100f

        binding.starTop.alpha = 0f
        binding.starTop.rotationY = 180f

        binding.crescentBottom.alpha = 0f
        binding.crescentBottom.translationX = 100f
        binding.crescentBottom.translationY = 100f

        binding.starBottom.alpha = 0f
        binding.starBottom.rotationY = 180f

        binding.appName.alpha = 0f
        binding.appName.translationY = 50f

        binding.tagline.alpha = 0f
        binding.tagline.translationY = 30f

        // Animation 1: Circle appears with scale
        val circleScaleX = ObjectAnimator.ofFloat(binding.logoCircle, View.SCALE_X, 0f, 1f)
        val circleScaleY = ObjectAnimator.ofFloat(binding.logoCircle, View.SCALE_Y, 0f, 1f)
        val circleAlpha = ObjectAnimator.ofFloat(binding.logoCircle, View.ALPHA, 0f, 1f)

        val circleAnim = AnimatorSet().apply {
            playTogether(circleScaleX, circleScaleY, circleAlpha)
            duration = 500
            interpolator = OvershootInterpolator(1.5f)
        }

        // Animation 2: Play button bounces in
        val playScaleX = ObjectAnimator.ofFloat(binding.playButton, View.SCALE_X, 0f, 1.2f, 1f)
        val playScaleY = ObjectAnimator.ofFloat(binding.playButton, View.SCALE_Y, 0f, 1.2f, 1f)
        val playAlpha = ObjectAnimator.ofFloat(binding.playButton, View.ALPHA, 0f, 1f)

        val playAnim = AnimatorSet().apply {
            playTogether(playScaleX, playScaleY, playAlpha)
            duration = 400
            interpolator = OvershootInterpolator(2f)
            startDelay = 300
        }

        // Animation 3: Top crescent slides in
        val crescentTopX = ObjectAnimator.ofFloat(binding.crescentTop, View.TRANSLATION_X, -100f, 0f)
        val crescentTopY = ObjectAnimator.ofFloat(binding.crescentTop, View.TRANSLATION_Y, -100f, 0f)
        val crescentTopAlpha = ObjectAnimator.ofFloat(binding.crescentTop, View.ALPHA, 0f, 1f)

        val crescentTopAnim = AnimatorSet().apply {
            playTogether(crescentTopX, crescentTopY, crescentTopAlpha)
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 500
        }

        // Animation 4: Top star flips in
        val starTopRotate = ObjectAnimator.ofFloat(binding.starTop, View.ROTATION_Y, 180f, 0f)
        val starTopAlpha = ObjectAnimator.ofFloat(binding.starTop, View.ALPHA, 0f, 1f)

        val starTopAnim = AnimatorSet().apply {
            playTogether(starTopRotate, starTopAlpha)
            duration = 400
            startDelay = 600
        }

        // Animation 5: Bottom crescent slides in
        val crescentBottomX = ObjectAnimator.ofFloat(binding.crescentBottom, View.TRANSLATION_X, 100f, 0f)
        val crescentBottomY = ObjectAnimator.ofFloat(binding.crescentBottom, View.TRANSLATION_Y, 100f, 0f)
        val crescentBottomAlpha = ObjectAnimator.ofFloat(binding.crescentBottom, View.ALPHA, 0f, 1f)

        val crescentBottomAnim = AnimatorSet().apply {
            playTogether(crescentBottomX, crescentBottomY, crescentBottomAlpha)
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 700
        }

        // Animation 6: Bottom star flips in
        val starBottomRotate = ObjectAnimator.ofFloat(binding.starBottom, View.ROTATION_Y, 180f, 0f)
        val starBottomAlpha = ObjectAnimator.ofFloat(binding.starBottom, View.ALPHA, 0f, 1f)

        val starBottomAnim = AnimatorSet().apply {
            playTogether(starBottomRotate, starBottomAlpha)
            duration = 400
            startDelay = 800
        }

        // Animation 7: App name slides up
        val nameY = ObjectAnimator.ofFloat(binding.appName, View.TRANSLATION_Y, 50f, 0f)
        val nameAlpha = ObjectAnimator.ofFloat(binding.appName, View.ALPHA, 0f, 1f)

        val nameAnim = AnimatorSet().apply {
            playTogether(nameY, nameAlpha)
            duration = 400
            startDelay = 900
        }

        // Animation 8: Tagline fades in
        val taglineY = ObjectAnimator.ofFloat(binding.tagline, View.TRANSLATION_Y, 30f, 0f)
        val taglineAlpha = ObjectAnimator.ofFloat(binding.tagline, View.ALPHA, 0f, 1f)

        val taglineAnim = AnimatorSet().apply {
            playTogether(taglineY, taglineAlpha)
            duration = 400
            startDelay = 1000
        }

        // Play all animations
        val mainAnimator = AnimatorSet().apply {
            playTogether(
                circleAnim,
                playAnim,
                crescentTopAnim,
                starTopAnim,
                crescentBottomAnim,
                starBottomAnim,
                nameAnim,
                taglineAnim
            )
        }

        mainAnimator.doOnEnd {
            // Small delay then go to main activity
            binding.root.postDelayed({
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                // Fade transition
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }, 500)
        }

        mainAnimator.start()
    }
}
