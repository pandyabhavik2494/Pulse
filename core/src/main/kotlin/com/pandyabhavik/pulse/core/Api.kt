package com.pandyabhavik.pulse.core

// SHARED CONTRACT: the public API the app uses lives in these objects, one file each:
//   Energy      -> Energy.kt      (BMR, resting burn so far, resting/active split)
//   Trend       -> Trend.kt       (exponential moving average weight trend)
//   Forecaster  -> Forecaster.kt  (milestones, rate, projected dates, what-if)
//   Motivation  -> Motivation.kt  (streak, wins, copy for nudges, recap, celebrations)
// Shared text formatting (dates, numbers, kg) is in Format.kt.
