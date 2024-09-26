package com.example.simpledrawingapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView : DrawingView? = null
    private var mImageBtnCurrentPaint : ImageButton? = null
    private var customProgresssDialogue : Dialog? = null


    val openGalleryLauncher : ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result->
            if(result.resultCode== RESULT_OK && result.data!=null){
                val imageBg : ImageView = findViewById(R.id.iv_bg)

                imageBg.setImageURI(result.data?.data)
            }
        }

    private var storageActivityResult : ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val permission = it.key
                val isGranted = it.value
                if (isGranted) {
                    if (permission == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(this, "permission granted for access storage", Toast.LENGTH_SHORT).show()
                        val pickIntent =
                            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        openGalleryLauncher.launch(pickIntent)
                    }

                } else {
                        Toast.makeText(this, "permission denied", Toast.LENGTH_SHORT).show()
                }
            }

        }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        drawingView?.setSizeForBrush(20.toFloat())
        val linearLayout =findViewById<LinearLayout>(R.id.ll_paint_color)

        mImageBtnCurrentPaint=linearLayout[0] as ImageButton
        mImageBtnCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallete_pressed))

       val brushBtn = findViewById<ImageButton>(R.id.ib_brush_button)
        brushBtn.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val undoBtn = findViewById<ImageButton>(R.id.ib_undo_btn)
        undoBtn.setOnClickListener {
            drawingView?.onClikUndo()
        }

        val redoBtn = findViewById<ImageButton>(R.id.ib_redo_btn)
        redoBtn.setOnClickListener {
            drawingView?.onClickRedo()
        }

        val permissionBtn = findViewById<ImageButton>(R.id.ib_permission_btn)
        permissionBtn.setOnClickListener {
            showrequestRational()
        }


        val clearBtn = findViewById<ImageButton>(R.id.ib_new_btn)
        clearBtn.setOnClickListener {
            drawingView?.clearCanvas()
        }

        val saveBtn = findViewById<ImageButton>(R.id.ib_save_btn)
        saveBtn.setOnClickListener{

            if(isReadStorageAllowed()){
                customDialogue()
                lifecycleScope.launch {
                    val flDrawingView : FrameLayout = findViewById(R.id.bg_image_fl)
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }
    }

    private fun isReadStorageAllowed():Boolean{
        val result=ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun showrequestRational() {
        if(shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)){
            showDialog("permission for external storage","Permission for external storage is required to upload background image into the canvas")
        }else{
            storageActivityResult.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun showDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton("Ok"){
                dialog,which->
                Toast.makeText(this@MainActivity,"ok pressed",Toast.LENGTH_SHORT).show()
                storageActivityResult.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                dialog.dismiss()
            }
            setNeutralButton("Cancel"){
                dialog,which->
                Toast.makeText(this@MainActivity,"cancel pressed",Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            create()
            show()
        }

    }

    private fun showBrushSizeChooserDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")

        val exSmallBtn =  brushDialog.findViewById<ImageButton>(R.id.ib_exSmall_brush)
        exSmallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(5.toFloat())
            brushDialog.dismiss()
        }

        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.ib_brush_small)
        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View){
        if(view != mImageBtnCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallete_pressed)
            )

            mImageBtnCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallete_normal)
            )
            mImageBtnCurrentPaint = view

        }
    }

    private fun getBitmapFromView(view: View):Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)

        val canvas = Canvas(returnedBitmap)
        val bgDrawing = view.background
        if(bgDrawing!=null){
            bgDrawing.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap:Bitmap?):String{
        var result=""
        withContext(Dispatchers.IO){
            if(mBitmap != null){
                try{
                    val byte = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90,byte)
                    val file = File(externalCacheDir?.absoluteFile.toString()+
                    File.separator + "simpleDrawingApp"+System.currentTimeMillis()/1000 + ".png")
                    val fileOutputStream = FileOutputStream(file)
                    fileOutputStream.write(byte.toByteArray())
                    fileOutputStream.close()

                    result = file.absolutePath

                    runOnUiThread {
                        customDialogCancelable()
                        if(result.isNotEmpty()){
                            Toast.makeText(this@MainActivity,"File saved successfully : $result",Toast.LENGTH_SHORT).show()
                            shareImage(result)
                        }else{
                            Toast.makeText(this@MainActivity," something goes wrong",Toast.LENGTH_SHORT).show()
                        }
                    }

                }
                catch (e : Exception){
                    result=""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun customDialogue(){
        customProgresssDialogue = Dialog(this)
        customProgresssDialogue!!.setContentView(R.layout.custom_dialog)
        customProgresssDialogue!!.setCancelable(false)
        customProgresssDialogue?.show()

    }

    private fun customDialogCancelable(){
        if(customProgresssDialogue!=null){
            customProgresssDialogue?.dismiss()
            customProgresssDialogue=null
        }
    }

    private fun shareImage(result:String){
        MediaScannerConnection.scanFile(this, arrayOf(result),null){
            path, uri ->
            val intent = Intent()
            intent.action = Intent.ACTION_SEND
            intent.putExtra(Intent.EXTRA_STREAM,"Share")
            intent.type="image/png"
            startActivity(Intent.createChooser(intent,"Share"))

        }
    }

}