package com.game.mymemory

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.game.mymemory.models.BoardSize
import com.game.mymemory.models.MemoryGame
import com.game.mymemory.models.UserGames
import com.game.mymemory.models.UserImageList
import com.game.mymemory.utils.EXTRA_GAME_NAME
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 1
        private const val RC_GOOGLE_SIGN_IN = 100
    }

    private lateinit var clRoot: CoordinatorLayout
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView
    private lateinit var rvBoard: RecyclerView

    private lateinit var memoryGame: MemoryGame
    private var boardSize: BoardSize = BoardSize.EASY
    private lateinit var adapter: MemoryBoardAdapter
    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private var userEmail: String? = null
    private lateinit var auth: FirebaseAuth

    var itemLogin: MenuItem? = null
    var itemLogout: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)

        userEmail = intent.getStringExtra("USER_EMAIL")

        setupBoard()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        itemLogin = menu?.findItem(R.id.mi_login)
        itemLogout = menu?.findItem(R.id.mi_logout)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (!isLoggedIn()) {
            itemLogout?.setVisible(false)
            itemLogin?.setVisible(true)
        } else {
            itemLogout?.setVisible(true)
            itemLogin?.setVisible(false)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mi_refresh -> {
                if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog(getString(R.string.question_quit), null, View.OnClickListener {
                        setupBoard()
                    })
                } else {
                    setupBoard()
                }
            }
            R.id.mi_new_size -> {
                showNewSizeDialog()
                return true
            }
            R.id.mi_custom -> {
                showCreationDialog()
                return true
            }
            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }
            R.id.mi_usergames -> {
                showUsersGameDialog()
                return true
            }
            R.id.mi_login -> {
                login()
                return true
            }
            R.id.mi_logout -> {
                logout()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun logout() {
        auth.signOut()
        userEmail = null
        Snackbar.make(clRoot, getString(R.string.loggedout_success), Snackbar.LENGTH_SHORT).show()
    }

    private fun login() {
        // Initialize Firebase Auth
        auth = Firebase.auth

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(this, gso)

        //Create dialog for login
        val dialogBuilder = AlertDialog.Builder(this)
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_login, null)
        dialogBuilder.setView(dialogView)

        dialogBuilder.setPositiveButton(
            getString(R.string.login),
            object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface, id: Int) {
                    val signInIntent = client.signInIntent
                    startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
                }
            })
        dialogBuilder.setNegativeButton(
            getString(R.string.cancel),
            object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface?, which: Int) {
                }
            })

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    private fun showUsersGameDialog() {
        if (!isLoggedIn()) {
            Toast.makeText(this, "You have to log in first", Toast.LENGTH_LONG).show()
        } else {
            val games = mutableListOf<UserGames>()
            db.collection("games").get().addOnCompleteListener {
                if (it.isSuccessful) {
                    for (document in it.result!!) {
                        val userImageList = document.toObject(UserImageList::class.java)
                        if (userImageList.userEmail == null) {
                            Log.e(TAG, "Got null user data from Firestore")
                            Snackbar.make(clRoot, getString(R.string.user_games_not_found), Snackbar.LENGTH_SHORT).show()
                            return@addOnCompleteListener
                        }
                        if (userImageList.userEmail?.trim() == userEmail?.trim()) {
                            games.add(UserGames(document.id, userImageList))
                        }
                    }
                } else {
                    Log.e(TAG, "Exception when retrieving user games from Firestore", it.exception)
                }
                if (games.size == 0) {
                    Snackbar.make(clRoot, getString(R.string.user_games_not_found), Snackbar.LENGTH_SHORT).show()
                } else {
                    val gamesListView =
                        LayoutInflater.from(this).inflate(R.layout.dialog_list_games, null)
                    val spGames = gamesListView.findViewById<Spinner>(R.id.spGames)
                    val gamesSpinner: MutableList<String> = ArrayList()
                    gamesSpinner.add(0, getString(R.string.select_game))
                    for (item in games) {
                        gamesSpinner.add(item.gameName)
                    }
                    val dataAdapter: ArrayAdapter<String> = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, gamesSpinner)
                    dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spGames.setAdapter(dataAdapter)

                    showAlertDialog(
                        getString(R.string.choose_game),
                        gamesListView,
                        View.OnClickListener {
                            val gameToDownload = spGames.selectedItem.toString()
                            val itemPosition = spGames.selectedItemPosition
                            if (itemPosition == 0) {
                                Snackbar.make(clRoot, getString(R.string.no_game), Snackbar.LENGTH_SHORT).show()
                            } else {
                                loadGame(
                                    games[itemPosition - 1].userImages.images!!, gameToDownload)
                            }
                        })
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if (customGameName == null) {
                Log.e(TAG, "Got null custom game name from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)!!
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account.idToken!!)
                Snackbar.make(clRoot, getString(R.string.loggedin_success), Snackbar.LENGTH_SHORT).show()
            } catch (e: ApiException) {
                // Google Sign In failed, update UI appropriately
                Log.w(TAG, "Google sign in failed", e)
                Snackbar.make(clRoot, getString(R.string.loggedin_error), Snackbar.LENGTH_SHORT).show()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showDownloadDialog() {
        val boardDownloadView =
            LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog(getString(R.string.fetch_game), boardDownloadView, View.OnClickListener {
            //Grab the text of the game name that the user wants to download
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadName)
            val gameToDownload = etDownloadGame.text.toString().trim()
            if (gameToDownload.isEmpty()) {
                Toast.makeText(this, "Enter the name of the game", Toast.LENGTH_LONG).show()
            } else {
                downloadGame(gameToDownload)
            }
        })
    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, "Invalid custom game data from Firestore")
                Snackbar.make(clRoot,getString(R.string.game_not_find) + ", '$customGameName'", Snackbar.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            loadGame(userImageList.images, customGameName)
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Exception when retrieving game", exception)
        }
    }

    private fun loadGame(images: List<String>, customGameName: String) {
        val numCards = images.size * 2
        boardSize = BoardSize.getByValue(numCards)
        customGameImages = images
        for (imageUrl in images) {
            Picasso.get().load(imageUrl).fetch()
        }
        Snackbar.make(clRoot,getString(R.string.play_now) + " '$customGameName'!",Snackbar.LENGTH_LONG).show()
        gameName = customGameName
        setupBoard()
    }

    private fun showCreationDialog() {
        if (!isLoggedIn()) {
            Toast.makeText(this, "You have to log in first", Toast.LENGTH_LONG).show()
        } else {
            val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
            val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

            showAlertDialog(getString(R.string.create_board), boardSizeView, View.OnClickListener {
                // Set a new value for the board size
                val desiredBoardSize = when (radioGroupSize.checkedRadioButtonId) {
                    R.id.rbEasy -> BoardSize.EASY
                    R.id.rbMedium -> BoardSize.MEDIUM
                    else -> BoardSize.HARD
                }
                //Navigate to a new activity
                val intent = Intent(this, CreateActivity::class.java)
                intent.putExtra("EXTRA_BOARD_SIZE", desiredBoardSize)
                intent.putExtra("USER_EMAIL", userEmail)
                startActivityForResult(intent, CREATE_REQUEST_CODE)
            })
        }
    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        when (boardSize) {
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }
        showAlertDialog(getString(R.string.choose_size), boardSizeView, View.OnClickListener {
            // Set a new value for the board size
            boardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            setupBoard()
        })
    }

    private fun showAlertDialog(
        title: String,
        view: View?,
        positiveClickListener: View.OnClickListener
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                positiveClickListener.onClick(null)
            }.show()
    }

    private fun setupBoard() {
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        when (boardSize) {
            BoardSize.EASY -> {
                tvNumMoves.text = getString(R.string.easy)
                tvNumPairs.text = getString(R.string.pairs, 0, 4)
            }
            BoardSize.MEDIUM -> {
                tvNumMoves.text = getString(R.string.medium)
                tvNumPairs.text = getString(R.string.pairs, 0, 9)
            }
            BoardSize.HARD -> {
                tvNumMoves.text = getString(R.string.hard)
                tvNumPairs.text = getString(R.string.pairs, 0, 12)
            }
        }
        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize, customGameImages)

        adapter = MemoryBoardAdapter(
            this,
            boardSize,
            memoryGame.cards,
            object : MemoryBoardAdapter.CardClickListener {
                override fun onCardClicked(position: Int) {
                    updateGameWithFlip(position)
                }
            })

        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    private fun updateGameWithFlip(position: Int) {
        //Error checking
        if (memoryGame.haveWonGame()) {
            //Alert user of invalid move
            Snackbar.make(clRoot, getString(R.string.won), Snackbar.LENGTH_LONG).show()
            return
        }
        if (memoryGame.isCardFaceUp(position)) {
            //Alert the user of invalid move
            Snackbar.make(clRoot, getString(R.string.invalid_move), Snackbar.LENGTH_SHORT).show()
            return
        }

        //Flip over the card
        if (memoryGame.flipCard(position)) {
            Log.i(TAG, "Found a match! Num pairs found: ${memoryGame.numPairsFound}")
            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full)
            ) as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text = getString(R.string.pairs, memoryGame.numPairsFound, boardSize.getNumPairs())

            if (memoryGame.haveWonGame()) {
                Snackbar.make(clRoot, getString(R.string.won_congratulations), Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA)).oneShot()
            }
        }
        tvNumMoves.text = getString(R.string.moves, memoryGame.getNumMoves())
        adapter.notifyDataSetChanged()
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth?.signInWithCredential(credential).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    userEmail = auth?.currentUser.email
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Authentication Failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun isLoggedIn(): Boolean = (userEmail != null)
}
