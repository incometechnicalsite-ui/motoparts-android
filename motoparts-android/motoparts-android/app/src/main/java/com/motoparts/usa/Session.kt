package com.motoparts.usa

import android.content.Context

class Session(ctx: Context) {
    private val sp = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)

    var token: String?
        get() = sp.getString("token", null)
        set(v) { sp.edit().putString("token", v).apply() }

    var userName: String?
        get() = sp.getString("name", null)
        set(v) { sp.edit().putString("name", v).apply() }

    var currency: String
        get() = sp.getString("currency", "USD") ?: "USD"
        set(v) { sp.edit().putString("currency", v).apply() }
}
