package com.example.androidslackclone

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.BaseCallback
import com.auth0.android.management.ManagementException
import com.auth0.android.management.UsersAPIClient
import com.auth0.android.result.UserProfile
import com.pusher.chatkit.CurrentUser
import com.pusher.chatkit.messages.Direction
import com.pusher.chatkit.rooms.Room
import com.pusher.chatkit.rooms.RoomSubscriptionEvent
import com.pusher.platform.network.wait
import com.pusher.util.Result
import elements.Error
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.content_main.*
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory


class MainActivity : AppCompatActivity(), RoomsAdapter.RoomClickListener,
	ChatUserAdapter.UserClickedListener {
	
	private lateinit var authenticationAPIClient: AuthenticationAPIClient
	lateinit var usersClient: UsersAPIClient
	private val mAdapter = RoomsAdapter(this)
	private val chatAdapter = ChatMessageAdapter()
	private val chatUserAdapter = ChatUserAdapter(this)
	
	private val slackCloneAPI:SlackCloneAPI by lazy {
		Retrofit.Builder()
			.baseUrl("https://wt-25e341bb2fca3ab10c862fb71cda965c-0.sandbox.auth0-extend.com/")
			.addConverterFactory(ScalarsConverterFactory.create())
			.addConverterFactory(GsonConverterFactory.create())
			.client(OkHttpClient.Builder().build())
			.build()
			.create(SlackCloneAPI::class.java)
	}
	
	
	
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(toolbar)
		
		
		val accessToken = intent.getStringExtra("access_token")
		
		
		val auth0 = Auth0(this)
		auth0.isOIDCConformant = true
		auth0.isLoggingEnabled = true
		authenticationAPIClient = AuthenticationAPIClient(auth0)
		
		
		
		usersClient = UsersAPIClient(auth0, accessToken)
		getProfile(accessToken)
		
		setupRecyclerView()
		setupClickListeners()
		
	}
	
	private fun setupClickListeners() {
		
		sendMessage.setOnClickListener {
			if (editTextMessage.text.isNotEmpty()){
				val sendMessageResult = SlackCloneApp.currentUser.sendMessage(5,editTextMessage.text.toString()).wait()
				when (sendMessageResult) { // Result<CurrentUser, Error>
					is Result.Success -> {
						Toast.makeText(this,"Message sending successful",Toast.LENGTH_SHORT).show()
					}
					is Result.Failure -> Log.d("SlackClone", sendMessageResult.error.toString())
				}
			}
		}
		
	}
	
	private fun setupRecyclerView() {
		
		with(recyclerViewRooms) {
			layoutManager = LinearLayoutManager(this@MainActivity)
			adapter = mAdapter
		}
		
		with(chatRecyclerView) {
			layoutManager = LinearLayoutManager(this@MainActivity)
			adapter = chatAdapter
		}
		
		with(recyclerViewMembers){
			layoutManager = LinearLayoutManager(this@MainActivity)
			adapter = chatUserAdapter
		}
		
	}
	
	private fun connectChatKit() {
		val result: Result<CurrentUser, Error>
		
		try {
			result = SlackCloneApp.chatManager.connect().wait()
			when (result) { // Result<CurrentUser, Error>
				is Result.Success -> {
					Log.d("Authentication", result.value.toString())
					SlackCloneApp.currentUser = result.value
					loadRooms()
					fetchUsers()
				}
				is Result.Failure -> Log.d("Authentication", result.error.toString())
			}
			
		} catch (e: Exception) {
			e.printStackTrace()
		}
		
	}
	
	private fun loadRooms() {
		
		val roomsResult = SlackCloneApp.currentUser.rooms
		runOnUiThread {
			
			for (room in roomsResult) {
				if (room.name == "General") {
					Log.d("SlackClone", "There is a channel called General")
					
					/*if (SlackCloneApp.currentUser.isSubscribedToRoom(room)){
						Log.d("SlackClone","User had subscribed before")
						fetchMessages(room)
					} else {
						Log.d("SlackClone","New time subscription")
						subscribeToRoom(room)
					}*/
					fetchMessages(room)
					subscribeToRoom(room)
					
				}
			}
			
			mAdapter.setList(roomsResult as ArrayList<Room>)
		}
		/*when (roomsResult) {
			
			is Result.Success -> {
				runOnUiThread {
					Log.d("Tag",roomsResult.value.toString())
					Log.d("Tag",roomsResult.value.size.toString())
					mAdapter.setList(roomsResult.value as ArrayList<Room>)
				}
				
			}
			
			is Result.Failure -> {
			
			}
			
		}*/
	}
	
	private fun subscribeToRoom(room: Room) {
		progressBarChat.visibility = View.VISIBLE
		SlackCloneApp.currentUser.subscribeToRoom(
			roomId = room.id,
			messageLimit = 100 // Optional, 10 by default
		) { event ->
			when (event) {
				is RoomSubscriptionEvent.NewMessage -> {
					progressBarChat.visibility = View.GONE
					chatAdapter.addItem(event.message)
					Log.d("SlackClone", event.message.text)
				}
				is RoomSubscriptionEvent.ErrorOccurred -> {
					Log.d("SlackClone", event.error.reason)
					Toast.makeText(this,"Error trying to subscribe to Room",
						Toast.LENGTH_SHORT).show()
				}
			}
		}
		
	}
	
	private fun fetchMessages(room: Room) {
		
		val messagesResult = SlackCloneApp.currentUser.fetchMessages(
			room.id,
			direction = Direction.OLDER_FIRST, // Optional, OLDER_FIRST by default
			limit = 100 // Optional, 10 by default
		).wait()
		
		when(messagesResult) {
			is Result.Success -> {
				chatAdapter.setList(messagesResult.value)
				progressBarChat.visibility = View.GONE
			}
			is Result.Failure -> {
				Log.d("SlackClone",messagesResult.error.reason)
			}
		}
		
	}
	
	private fun fetchUsers(){
		slackCloneAPI.getUsers().enqueue(object: Callback<List<ChatKitUser>> {
			override fun onFailure(call: Call<List<ChatKitUser>>, t: Throwable) {
				Log.d("SlackClone",t.message)
			}
			
			override fun onResponse(call: Call<List<ChatKitUser>>, response: Response<List<ChatKitUser>>) {
				Log.d("SlackClone",response.body().toString())
				chatUserAdapter.setList(response.body()!!)
			}
			
		})
	}
	
	private fun getProfile(accessToken: String) {
		authenticationAPIClient.userInfo(accessToken)
		  .start(object : BaseCallback<UserProfile, AuthenticationException> {
			  override fun onSuccess(userinfo: UserProfile) {
				  Log.d("SlackClone", "First onSuccess called")
				  
				  usersClient.getProfile(userinfo.id)
					.start(object : BaseCallback<UserProfile, ManagementException> {
						override fun onSuccess(profile: UserProfile) {
							Log.d("SlackClone", "Time to connect to ChatKit")
							SlackCloneApp.userEmail = profile.email
							connectChatKit()
							/*userProfile = profile
							runOnUiThread { refreshScreenInformation() }*/
						}
						
						override fun onFailure(error: ManagementException) {
							Log.d("SlackClone", error.message)
							Log.d("SlackClone", error.code)
							Log.d("SlackClone", error.localizedMessage)
							Log.d("SlackClone", error.stackTrace.toString())
							runOnUiThread { Toast.makeText(this@MainActivity, "User Profile Request Failed",
								Toast.LENGTH_SHORT).show() }
						}
					})
			  }
			  
			  override fun onFailure(error: AuthenticationException) {
					Log.d("SlackClone",error.message!!)
					Log.d("SlackClone",error.description)
					error.printStackTrace()
					runOnUiThread { Toast.makeText(this@MainActivity, "User Info Request Failed", Toast.LENGTH_SHORT).show() }
			  }
		  })
	}
	
	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		// Inflate the menu; this adds items to the action bar if it is present.
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}
	
	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		return when (item.itemId) {
			R.id.action_settings -> true
			else -> super.onOptionsItemSelected(item)
		}
	}
	
	private fun logout() {
		val intent = Intent(this, LoginActivity::class.java)
		intent.putExtra("clear_credentials", true)
		startActivity(intent)
		finish()
	}
	
	override fun onUserClicked(user: ChatKitUser) {
		
		val privateRoomName = if (user.hashCode()>SlackCloneApp.currentUser.hashCode()){
			user.id+"_"+SlackCloneApp.currentUser.id
		} else {
			SlackCloneApp.currentUser.id+"_"+user.id
		}
		
		val memberList = ArrayList<String>()
		memberList.add(user.id)
		
		val createRoomResult = SlackCloneApp.currentUser.createRoom(privateRoomName,true,memberList).wait()
		
		when(createRoomResult){
			
			is Result.Success -> {
				Log.d("SlackClone",createRoomResult.value.name)
				chatAdapter.clear()
				subscribeToRoom(createRoomResult.value)
				fetchMessages(createRoomResult.value)
			}
			is Result.Failure -> {
				Log.d("SlackClone",createRoomResult.error.reason)
			}
		
		}
		
	}
	
	override fun onRoomClicked(item: Room) {
		chatAdapter.clear()
		subscribeToRoom(item)
		fetchMessages(item)
	}
	
}
