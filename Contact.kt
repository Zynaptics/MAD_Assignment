package com.example.contact_app_recycler_view

import android.net.Uri

data class Contact(
    var name: String,
    var phone: String,
    var profileImageUri: String? = null  // Store URI as string for persistence
)