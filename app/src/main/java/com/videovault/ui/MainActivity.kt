package com.videovault.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.videovault.R
import com.videovault.databinding.ActivityMainBinding
import com.videovault.ui.home.HomeFragment
import com.videovault.ui.online.OnlinePlayerFragment
import com.videovault.ui.downloads.DownloadsFragment
import com.videovault.ui.local.LocalVideosFragment
import com.videovault.ui.settings.SettingsFragment
import com.videovault.util.PermissionUtils

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (!perms.values.all { it }) Snackbar.make(binding.root, R.string.permission_denied, Snackbar.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNav(); checkPerms(); handleIntent(intent)
        if (savedInstanceState == null) loadFragment(HomeFragment())
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(i: Intent?) {
        i?.getStringExtra(Intent.EXTRA_TEXT)?.let { s ->
            if (s.contains("twitter.com") || s.contains("x.com")) loadFragment(HomeFragment())
        }
    }

    private fun setupNav() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            loadFragment(when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_online -> OnlinePlayerFragment()
                R.id.nav_downloads -> DownloadsFragment()
                R.id.nav_local -> LocalVideosFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> return@setOnItemSelectedListener false
            }); true
        }
    }

    private fun loadFragment(f: Fragment) { supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, f).commit() }

    private fun checkPerms() {
        val req = mutableListOf<String>()
        if (!PermissionUtils.hasStoragePermission(this)) req.addAll(PermissionUtils.getRequiredStoragePermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            req.add(Manifest.permission.POST_NOTIFICATIONS)
        if (req.isNotEmpty()) permLauncher.launch(req.toTypedArray())
    }
}
