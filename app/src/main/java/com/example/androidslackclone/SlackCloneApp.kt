package com.example.androidslackclone

import android.app.Application
import com.pusher.chatkit.AndroidChatkitDependencies
import com.pusher.chatkit.ChatManager
import com.pusher.chatkit.ChatkitTokenProvider
import com.pusher.chatkit.CurrentUser

/**
 * Created by Idorenyin Obong on 04/10/2018
 *
 */

class SlackCloneApp : Application() {
	
	companion object {
		lateinit var userEmail: String
		lateinit var currentUser: CurrentUser
		private const val INSTANCE_LOCATOR = "v1:us1:d530db18-2073-47fe-89d1-badf424f29a5"
		//private const val TOKEN_PROVIDER_ENDPOINT = "http://10.0.2.2:3000/token"
		private const val TOKEN_PROVIDER_ENDPOINT =
			"https://wt-25e341bb2fca3ab10c862fb71cda965c-0.sandbox.auth0-extend.com/slack-clone/token"
		private val tokenProvider: ChatkitTokenProvider by lazy {
			ChatkitTokenProvider(TOKEN_PROVIDER_ENDPOINT, userEmail)
		}
		val chatManager: ChatManager by lazy {
			ChatManager(
				instanceLocator = INSTANCE_LOCATOR, userId = userEmail,
				dependencies = AndroidChatkitDependencies(tokenProvider = tokenProvider)
			)
		}
	}
	
}
