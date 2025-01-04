package jp.su.mnb.oac

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import jp.su.mnb.oac.Fragment.MapFragment
import jp.su.mnb.oac.Fragment.QrFragment
import jp.su.mnb.oac.R
import jp.su.mnb.oac.Fragment.TrackingFragment
import com.google.android.material.bottomnavigation.BottomNavigationView


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初期表示のフラグメント
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, MapFragment())
            .commit()

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> {
                    replaceFragment(MapFragment())
                    true
                }
                R.id.nav_qr -> {
                    replaceFragment(QrFragment())
                    true
                }
                R.id.nav_tracking -> {
                    replaceFragment(TrackingFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

}
