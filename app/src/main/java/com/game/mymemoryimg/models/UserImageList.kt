package com.game.mymemoryimg.models

import com.google.firebase.firestore.PropertyName

data class UserImageList(
        @PropertyName("images")
        val images: List<String>? = null,
        @PropertyName("userEmail")
        val userEmail: String? = null
)