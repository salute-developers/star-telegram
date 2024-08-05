package ru.sberdevices.sbdv.auth.view

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ViewFlipper
import androidx.annotation.MainThread
import org.telegram.messenger.R
import ru.sberdevices.common.logger.Logger

@Suppress("ViewConstructor")
@MainThread
class QrLoginView(
    context: Context,
    onPhoneLoginClickListener: (View) -> Unit,
) : FrameLayout(context), QrLoginViewController.View {

    private val logger = Logger.get("QrLoginView")

    private val imageView: ImageView
    private val progressBar: ProgressBar
    private val viewFlipper: ViewFlipper
    private val thirdPage: View

    private var leftForPhoneLogin: Boolean = false

    init {
        val view = inflate(context, R.layout.sbdv_qr_login, this)

        viewFlipper = view.findViewById<ViewFlipper>(R.id.viewFlipper).apply {
            setInAnimation(context, R.anim.fade_in)
            setOutAnimation(context, R.anim.fade_out)
        }

        val firstPage = view.findViewById<View>(R.id.qr_login_first_page)
        firstPage.apply {
            findViewById<Button>(R.id.sbdv_qr_first_page_ready_button).setOnClickListener {
                viewFlipper.showNext()
            }

            findViewById<Button>(R.id.sbdv_qr_first_page_login_by_phone).setOnClickListener {
                leftForPhoneLogin = true
                onPhoneLoginClickListener(this)
            }
        }

        val secondPage = view.findViewById<View>(R.id.qr_login_second_page)
        secondPage.apply {
            findViewById<ImageButton>(R.id.sbdv_qr_login_second_page_back_button).setOnClickListener {
                viewFlipper.showPrevious()
            }
            findViewById<Button>(R.id.sbdv_qr_login_second_page_login_by_phone).setOnClickListener {
                leftForPhoneLogin = true
                onPhoneLoginClickListener(this)
            }

            imageView = findViewById(R.id.sbdv_qr_login_second_page_qr_image)
            progressBar = findViewById(R.id.sbdv_qr_login_second_page_progress)
        }

        thirdPage = view.findViewById<View>(R.id.qr_login_third_page).apply {
            findViewById<ImageButton>(R.id.sbdv_qr_login_third_page_back_button).setOnClickListener {
                viewFlipper.showPrevious()
            }
            findViewById<Button>(R.id.sbdv_qr_login_third_page_login_by_phone).setOnClickListener {
                leftForPhoneLogin = true
                onPhoneLoginClickListener(this)
            }
        }
    }

    override fun leftForPhoneLogin(): Boolean {
        return leftForPhoneLogin
    }

    override fun showQr(qrCode: Bitmap) {
        logger.verbose { "show() with $qrCode" }

        imageView.setImageBitmap(qrCode)

        imageView.visibility = View.VISIBLE
        progressBar.visibility = View.INVISIBLE
    }

    override fun hideQr() {
        logger.verbose { "hideQr()" }

        imageView.visibility = View.INVISIBLE
        progressBar.visibility = View.VISIBLE
    }

    override fun show2faPage() {
        logger.verbose { "show2faPage()" }
        viewFlipper.displayedChild = viewFlipper.indexOfChild(thirdPage)
    }
}
