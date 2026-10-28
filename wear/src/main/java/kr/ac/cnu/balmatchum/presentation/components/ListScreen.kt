package kr.ac.cnu.balmatchum.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * 둥근 화면에서 위아래로 스크롤되는 목록 화면의 공통 틀.
 * 화면마다 같은 코드를 반복하지 않으려고 만들었다. 사용 예는 DestinationScreen.kt.
 */
@Composable
fun ListScreen(
    title: String,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(title) }
            }
            content(spec)
        }
    }
}

/** 목록 안의 버튼 한 줄. */
fun TransformingLazyColumnScope.buttonItem(spec: TransformationSpec, label: String, onClick: () -> Unit) {
    item {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
            transformation = SurfaceTransformation(spec),
        ) { Text(label) }
    }
}

/** 목록 안의 글자 한 줄(누를 수 없음). */
fun TransformingLazyColumnScope.textItem(spec: TransformationSpec, text: String) {
    item {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        )
    }
}
