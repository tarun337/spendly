package com.expensemanager.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensemanager.app.data.GroupWithMembers
import com.expensemanager.app.data.Person
import com.expensemanager.app.ui.AppCard
import com.expensemanager.app.ui.AppViewModel
import com.expensemanager.app.ui.Avatar
import com.expensemanager.app.ui.IconBubble
import com.expensemanager.app.ui.SectionTitle
import com.expensemanager.app.ui.theme.extraColors

/** Manage favourite people and saved groups (e.g. Flatmates) used for quick splitting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(vm: AppViewModel, onBack: () -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val knownPeople by vm.knownPeople.collectAsStateWithLifecycle()

    var editFavs by remember { mutableStateOf(false) }
    // null = closed, id 0 = new group
    var editingGroup by remember { mutableStateOf<GroupWithMembers?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("People & groups") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionTitle("Groups") {
                    TextButton(onClick = { creatingGroup = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("New group")
                    }
                }
                Text(
                    "Add a whole group to a split in one tap — e.g. Flatmates.",
                    style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                )
            }
            if (groups.isEmpty()) {
                item {
                    AppCard(onClick = { creatingGroup = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Rounded.Groups, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Create your first group", fontWeight = FontWeight.SemiBold)
                                Text("Flatmates, Office, Trip…", style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
                            }
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            items(groups, key = { it.group.id }) { g ->
                AppCard(onClick = { editingGroup = g }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Groups, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${g.members.size} ${if (g.members.size == 1) "person" else "people"} · " + g.members.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.Rounded.Edit, "Edit ${g.group.name}", tint = extraColors.subtle)
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle("Favourites") {
                    TextButton(onClick = { editFavs = true }) {
                        Icon(if (favourites.isEmpty()) Icons.Rounded.Add else Icons.Rounded.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text(if (favourites.isEmpty()) "Add" else "Edit")
                    }
                }
                Text(
                    "Favourites show at the top when you split.",
                    style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                )
            }
            if (favourites.isEmpty()) {
                item {
                    AppCard(onClick = { editFavs = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Rounded.Star, MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("No favourites yet", fontWeight = FontWeight.SemiBold)
                                Text("Pick the people you split with most", style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
                            }
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                item {
                    AppCard {
                        favourites.forEach { p ->
                            PersonRow(p, checked = null, subtitle = p.phone, favourite = true, onStar = { vm.toggleFavourite(p) }) {}
                        }
                    }
                }
            }
        }
    }

    if (editFavs) {
        ContactPickerDialog(
            title = "Favourites",
            initial = favourites,
            recentPeople = knownPeople,
            favourites = emptyList(),
            onDismiss = { editFavs = false },
            onDone = { vm.setFavourites(it); editFavs = false },
        )
    }

    if (creatingGroup || editingGroup != null) {
        GroupEditorDialog(
            group = editingGroup,
            recentPeople = knownPeople,
            favourites = favourites,
            existingNames = groups.filter { it.group.id != editingGroup?.group?.id }.map { it.group.name },
            onDismiss = { creatingGroup = false; editingGroup = null },
            onSave = { name, members ->
                vm.saveGroup(editingGroup?.group?.id ?: 0, name, members)
                creatingGroup = false; editingGroup = null
            },
            onDelete = editingGroup?.let { g -> { vm.deleteGroup(g.group.id); editingGroup = null } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GroupEditorDialog(
    group: GroupWithMembers?,
    recentPeople: List<String>,
    favourites: List<Person>,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, List<Person>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(group?.group?.name.orEmpty()) }
    val members = remember { mutableStateListOf<Person>().apply { group?.members?.forEach { add(Person(it.name, it.phone)) } } }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val duplicate = existingNames.any { it.equals(name.trim(), ignoreCase = true) }
    val canSave = name.isNotBlank() && members.isNotEmpty() && !duplicate

    FullScreenLayer(onDismiss) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                TopAppBar(
                    title = { Text(if (group == null) "New group" else "Edit group") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") } },
                    actions = {
                        if (onDelete != null) IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, "Delete group", tint = extraColors.expense)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    windowInsets = WindowInsets(0),
                )
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it.take(30) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Group name") },
                            placeholder = { Text("e.g. Flatmates") },
                            singleLine = true,
                            isError = duplicate,
                            supportingText = if (duplicate) ({ Text("A group with this name already exists") }) else null,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                    if (group == null && name.isBlank()) {
                        item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Flatmates", "Office", "Trip", "College friends")
                                    .filter { s -> existingNames.none { it.equals(s, true) } }
                                    .forEach { s -> SuggestionChip(onClick = { name = s }, label = { Text(s) }) }
                            }
                        }
                    }
                    item {
                        SectionTitle("Members (${members.size})")
                    }
                    items(members.toList(), key = { it.name }) { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.name)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                                if (p.phone.isNotBlank()) Text(p.phone, style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
                            }
                            IconButton(onClick = { members.remove(p) }) { Icon(Icons.Rounded.Close, "Remove ${p.name}", tint = extraColors.subtle) }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(if (members.isEmpty()) "Add members" else "Add / remove members")
                        }
                    }
                }
                Button(
                    onClick = { onSave(name.trim(), members.toList()) },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) { Text("Save group") }
            }
        }
    }

    if (picking) {
        ContactPickerDialog(
            title = "Group members",
            initial = members.toList(),
            recentPeople = recentPeople,
            favourites = favourites,
            onDismiss = { picking = false },
            onDone = { picked -> members.clear(); members.addAll(picked); picking = false },
        )
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${group?.group?.name}\"?") },
            text = { Text("Past splits are not affected.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = extraColors.expense) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
