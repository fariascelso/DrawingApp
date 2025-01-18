package com.example.drawingapp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.applyCanvas
import com.example.drawingapp.ui.theme.DrawingAppTheme
import kotlinx.coroutines.launch
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DrawingAppTheme {
                PaintApp()
            }
        }
    }
}

@Composable
fun PaintApp() {
    val context = LocalContext.current.applicationContext
    val coroutineScope = rememberCoroutineScope()

    var currentColor by remember { mutableStateOf(Color.Black) }
    val lines = remember { mutableListOf<Line>() }
    var brushSize by remember { mutableFloatStateOf(10f) }
    var isEraser by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Requer Permissão", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //launcher.launch(Manifest.permission.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) //permission.WRITE_EXTERNAL_STORAGE
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column {

            Row(
                Modifier.fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorPicker { selectedColor ->
                    currentColor = selectedColor
                    isEraser = false
                }

                BrushSizeSelector(brushSize,
                    onSizeSelected = { selectedSize ->
                        brushSize = selectedSize
                    },
                    isEraser = isEraser,
                    keepMode = { keepEraserMode ->
                        isEraser = keepEraserMode
                    }
                )
            }

            Row(
                Modifier.fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Button(onClick = { isEraser = true }) {
                    Text("Borracha")
                }

                Button(onClick = { lines.clear() }) {
                    Text("Limpar")
                }

                Button(onClick = {
                    coroutineScope.launch {
                        saveDrawingToGallery(context, lines)
                    }
                }) {
                    Text("Salvar")
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()
            .background(Color.White)
            .pointerInput(true) {
                detectDragGestures { change, dragAmount ->
                    change.consume()

                    val line = Line(
                        start = change.position - dragAmount,
                        end = change.position,
                        color = if (isEraser) Color.White else currentColor,
                        strokeWidth = brushSize
                    )
                    lines.add(line)
                }
            }) {
            lines.forEach { line ->
                drawLine(
                    color = line.color,
                    start = line.start,
                    end = line.end,
                    strokeWidth = line.strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun ColorPicker(onColorSelected: (Color) -> Unit){
    val context = LocalContext.current.applicationContext
    val colorMap = mapOf(
        Color.Red to "Red",
        Color.Blue to "Blue",
        Color.Black to "Black")

    Row {
        colorMap.forEach { (color, name) ->
            Box(Modifier
                .size(40.dp)
                .background(color, CircleShape)
                .padding(12.dp)
                .clickable {
                    onColorSelected(color)
                    Toast.makeText(context, name, Toast.LENGTH_LONG).show()
                }
            )

            Spacer(modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
fun BrushSizeSelector(
    currentSize: Float,
    onSizeSelected: (Float) -> Unit,
    isEraser: Boolean,
    keepMode: (Boolean) -> Unit
) {
    var sizeText by remember { mutableStateOf(currentSize.toString()) }

    Row {
        BasicTextField(
            value = sizeText,
            onValueChange = {
                sizeText = it
                val newSize = it.toFloatOrNull() ?: currentSize
                onSizeSelected(newSize)
                keepMode(isEraser)
            },
            textStyle = TextStyle(fontSize = 16.sp),
            modifier = Modifier
                .width(60.dp)
                .background(Color.Gray, CircleShape)
                .padding(8.dp)
        )
        Text(" px", Modifier.align(Alignment.CenterVertically))
    }
}

data class Line(val start: Offset,
                val end: Offset,
                val color: Color,
                val strokeWidth: Float = 10F
)

suspend fun saveDrawingToGallery(context: Context, lines: List<Line>) {
    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)

    bitmap.applyCanvas {
        drawColor(android.graphics.Color.WHITE)
        lines.forEach { line ->
            val paint = android.graphics.Paint().apply {
                color = line.color.toArgb()
                strokeWidth = line.strokeWidth
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            drawLine(line.start.x, line.start.y, line.end.x, line.end.y, paint)
        }
    }

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "drawing_${System.currentTimeMillis()}.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PaintApp")
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    if (uri != null) {
        val outputStream: OutputStream? = resolver.openOutputStream(uri)
        outputStream.use {
            if (it != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        Toast.makeText(context, "Salvo na Galeria", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Falha ao salvar", Toast.LENGTH_SHORT).show()
    }
}

@Preview(showBackground = true)
@Composable
fun Preview() {
    DrawingAppTheme {
        PaintApp()
    }
}