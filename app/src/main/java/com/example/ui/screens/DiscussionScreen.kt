package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionScreen(viewModel: MainViewModel) {
    val apiKey by viewModel.apiKey.collectAsState()
    val models by viewModel.models.collectAsState()
    val isFetchingModels by viewModel.isFetchingModels.collectAsState()
    val selectedModel1 by viewModel.selectedModel1.collectAsState()
    val selectedModel2 by viewModel.selectedModel2.collectAsState()
    val maxRounds by viewModel.maxRounds.collectAsState()
    val maxBudget by viewModel.maxBudget.collectAsState()
    val discussionState by viewModel.discussionState.collectAsState()
    val isRunning by viewModel.isDiscussionRunning.collectAsState()
    val currentStage by viewModel.currentStage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()

    var topicInput by remember { mutableStateOf("") }
    var expandedModel1Dropdown by remember { mutableStateOf(false) }
    var expandedModel2Dropdown by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("مجلس الذكاء الاصطناعي (AI Council)", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(
                            onClick = { viewModel.setShowSettings(true) },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Topic input section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("ما هو الموضوع، الفكرة، أو السؤال للنقاش؟", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            OutlinedTextField(
                                value = topicInput,
                                onValueChange = { topicInput = it },
                                placeholder = { Text("مثال: هل الذكاء الاصطناعي العام (AGI) ممكن خلال العقد القادم؟") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .testTag("topic_input"),
                                maxLines = 5
                            )

                            // Model selection & config
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Model 1 Selector
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("النموذج الأول", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                            .clickable { expandedModel1Dropdown = true }
                                            .padding(12.dp)
                                            .testTag("model1_dropdown")
                                    ) {
                                        val m1Obj = models.find { it.id == selectedModel1 }
                                        Text(m1Obj?.name ?: (if (selectedModel1.isNotBlank()) selectedModel1 else "اختر نموذج..."), maxLines = 1)
                                        DropdownMenu(
                                            expanded = expandedModel1Dropdown,
                                            onDismissRequest = { expandedModel1Dropdown = false }
                                        ) {
                                            models.forEach { model ->
                                                DropdownMenuItem(
                                                    text = { Text("${model.name} ${if (model.isFree) "(مجاني)" else ""}") },
                                                    onClick = {
                                                        viewModel.setSelectedModel1(model.id)
                                                        expandedModel1Dropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Model 2 Selector
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("النموذج الثاني", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                            .clickable { expandedModel2Dropdown = true }
                                            .padding(12.dp)
                                            .testTag("model2_dropdown")
                                    ) {
                                        val m2Obj = models.find { it.id == selectedModel2 }
                                        Text(m2Obj?.name ?: (if (selectedModel2.isNotBlank()) selectedModel2 else "اختر نموذج..."), maxLines = 1)
                                        DropdownMenu(
                                            expanded = expandedModel2Dropdown,
                                            onDismissRequest = { expandedModel2Dropdown = false }
                                        ) {
                                            models.forEach { model ->
                                                DropdownMenuItem(
                                                    text = { Text("${model.name} ${if (model.isFree) "(مجاني)" else ""}") },
                                                    onClick = {
                                                        viewModel.setSelectedModel2(model.id)
                                                        expandedModel2Dropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Rounds & Budget info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("دورات المراجعة: $maxRounds", fontSize = 14.sp)
                                Text("ميزانية الطلبات: $maxBudget", fontSize = 14.sp)
                                Button(
                                    onClick = { viewModel.fetchModelCatalog() },
                                    enabled = !isFetchingModels,
                                    modifier = Modifier.testTag("fetch_models_button")
                                ) {
                                    if (isFetchingModels) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("تحديث النماذج")
                                    }
                                }
                            }

                            // Start / Cancel Discussion Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isRunning) {
                                    Button(
                                        onClick = { viewModel.startDiscussion(topicInput) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("start_discussion_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("بدء المناقشة")
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.cancelDiscussion() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("cancel_discussion_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("إيقاف المناقشة")
                                    }
                                }
                            }
                        }
                    }
                }

                // Error message area
                errorMessage?.let { err ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(err, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                // Progress / Stages area
                if (isRunning || currentStage != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("تقدم المناقشة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                currentStage?.let { stage ->
                                    Text(stage.name, fontWeight = FontWeight.SemiBold)
                                    Text(stage.description, fontSize = 14.sp)
                                    LinearProgressIndicator(
                                        progress = { stage.progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // Results Section: Final Synthesis
                discussionState?.let { result ->
                    result.synthesis?.let { synth ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("التركيب النهائي والحل التوافقي", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                    if (!synth.isSuccess) {
                                        Text("ملاحظة: فشل طلب التركيب النهائي، ويعرض أدناه أفضل تقدير متاح.", color = MaterialTheme.colorScheme.error)
                                    }
                                    HorizontalDivider()
                                    Text(synth.recommendation, fontSize = 15.sp)
                                }
                            }
                        }
                    }

                    // Participating Models & Detailed Responses
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("النماذج المشاركة:", fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    result.participatingModels.forEach { mId ->
                                        val mObj = models.find { it.id == mId }
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(mObj?.name ?: mId) }
                                        )
                                    }
                                }

                                Text("استجابات النماذج التحليلية:", fontWeight = FontWeight.Bold)
                                result.analyses.forEach { (mId, analysis) ->
                                    val mObj = models.find { it.id == mId }
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("نموذج: ${mObj?.name ?: mId}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(analysis.proposedAnswer, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Settings Dialog for API Key
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { viewModel.setShowSettings(false) },
                title = { Text("إعدادات مفتاح OpenRouter API") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var tempKey by remember { mutableStateOf(apiKey) }
                        Text("أدخل مفتاح OpenRouter API الخاص بك. يتم حفظه بشكل آمن على جهازك محلياً عبر Android Keystore.", fontSize = 13.sp)
                        OutlinedTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it },
                            label = { Text("مفتاح API") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("api_key_input")
                        )
                        Button(onClick = {
                            viewModel.saveApiKey(tempKey)
                            viewModel.setShowSettings(false)
                        }, modifier = Modifier.fillMaxWidth().testTag("save_api_key_button")) {
                            Text("حفظ المفتاح")
                        }
                        OutlinedButton(onClick = {
                            viewModel.clearApiKey()
                            viewModel.setShowSettings(false)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("حذف المفتاح المخزن")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.setShowSettings(false) }) {
                        Text("إغلاق")
                    }
                }
            )
        }
    }
}
