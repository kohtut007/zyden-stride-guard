package com.devzyden.stepcounterbyzyden.engine

import android.location.Location
import kotlin.math.sqrt

class StepFilterEngine {
    private var lastStepTime = 0L
    private val MAX_WALKING_SPEED_KMH = 20.0

    // Anti-Cheat Constants
    private val MAX_HUMAN_FORCE = 16.0  // လူတစ်ယောက် လမ်းလျှောက်ရင် ထွက်တဲ့ အမြင့်ဆုံး အရှိန်စွမ်းအား (G-Force Bound)
    private val MIN_HUMAN_FORCE = 9.5   // လမ်းလျှောက်တဲ့အခါ အနည်းဆုံး ရှိရမယ့် အရှိန် (Gravity အောက်ခြေ)

    /**
     * Anti-Cheat Engine:
     * Time-frequency နှင့် G-Force တွန်းအား (Accelerometer) နှစ်ခုလုံးကို Cross-Check လုပ်ပြီး စစ်ထုတ်ခြင်း
     */
    fun verifyStepValidity(currentTime: Long, x: Float, y: Float, z: Float): Boolean {
        // ၃ ဖက်မြင် တုန်ခါမှု စွမ်းအား စုစုပေါင်း (Magnitude Vector) ကို တရားဝင် ဖော်မြူလာဖြင့် တွက်ချက်ခြင်း
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())

        // စမ်းသပ်ချက် ၁: ဖုန်းကို အတင်းဆောင့်လှုပ်ရင် Magnitude က လူတစ်ယောက် လမ်းလျှောက်တာထက် အများကြီး ကျော်လွန်သွားလိမ့်မယ်
        // သို့မဟုတ် ဒီအတိုင်း ငြိမ်ငြိမ်လေး လှုပ်ရုံလှုပ်ရင် မပြည့်မီဘူး
        if (magnitude > MAX_HUMAN_FORCE || magnitude < MIN_HUMAN_FORCE) {
            return false // Cheat ဟု သတ်မှတ် (အတင်းလှုပ်ခြင်း)
        }

        // စမ်းသပ်ချက် ၂: Time Frequency Check (မပြောင်းလဲဘဲ ဆက်ထိန်းထားခြင်း)
        if (lastStepTime == 0L) {
            lastStepTime = currentTime
            return true
        }
        val timeDifference = currentTime - lastStepTime

        return if (timeDifference > 333) {
            lastStepTime = currentTime
            true
        } else {
            false
        }
    }

    /**
     * Vehicle Filter Logic: GPS ရဲ့ Live အရှိန်ကို စစ်ဆေးခြင်း
     */
    fun verifyUserIsNotInVehicle(location: Location?): Boolean {
        if (location == null) return true
        val speedKmh = location.speed * 3.6
        return speedKmh <= MAX_WALKING_SPEED_KMH
    }
}
