package com.expensemanager.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.expensemanager.app.data.GroupWithMembers
import com.expensemanager.app.data.Person
import com.expensemanager.app.ui.AppCard
import com.expensemanager.app.ui.Avatar
import com.expensemanager.app.ui.IconBubble
import com.expensemanager.app.ui.SectionTitle
import com.expensemanager.app.ui.theme.extraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun loadContacts(context: Context): List<Person> {
    val result = LinkedHashMap<String, Person>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
    )?.use { c ->
        val n = c.getColumnIndex(projection[0])
        val p = c.getColumnIndex(projection[1])
        while (c.moveToNext()) {
            val name = c.getString(n)?.trim().orEmpty()
            if (name.isNotEmpty() && name !in result) result[name] = Person(name, c.getString(p).orEmpty())
        }
    }
    return result.values.toList()
}

private fun List<Person>.hasName(name: String) = any { it.name.equals(name, ignoreCase = true) }

/**
 * Full-screen multi-select picker. Order: saved groups, favourites, recent split partners,
 * then device contacts. Any typed name can be added even if it isn't a contact.
 * Pass [onToggleFavourite] to show a star on each person.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerDialog(
    initial: List<Person>,
    recentPeople: List<String>,
    favourites: List<Person>,
    onDismiss: () -> Unit,
    onDone: (List<Person>) -> Unit,
    title: String = "Split with",
    groups: List<GroupWithMembers> = emptyList(),
    onToggleFavourite: ((Person) -> Unit)? = null,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var contacts by remember { mutableStateOf<List<Person>>(emptyList()) }
    LaunchedEffect(granted) {
        if (granted) contacts = withContext(Dispatchers.IO) { runCatching { loadContacts(context) }.getOrDefault(emptyList()) }
    }

    val selected = remember { mutableStateListOf<Person>().apply { addAll(initial) } }
    var query by remember { mutableStateOf("") }
    fun isSelected(name: String) = selected.hasName(name)
    fun toggle(p: Person) {
        val existing = selected.firstOrNull { it.name.equals(p.name, ignoreCase = true) }
        if (existing != null) selected.remove(existing) else selected.add(p)
    }
    fun isGroupSelected(g: GroupWithMembers) = g.members.isNotEmpty() && g.members.all { isSelected(it.name) }
    fun toggleGroup(g: GroupWithMembers) {
        if (isGroupSelected(g)) selected.removeAll { s -> g.members.any { it.name.equals(s.name, true) } }
        else g.members.filter { !isSelected(it.name) }.forEach { selected.add(Person(it.name, it.phone)) }
    }

    val q = query.trim()
    fun matches(p: Person) = q.isEmpty() || p.name.contains(q, true) || p.phone.contains(q)
    val shownGroups = groups.filter { g -> q.isEmpty() || g.group.name.contains(q, true) || g.members.any { it.name.contains(q, true) } }
    val shownFavs = favourites.filter(::matches)
    val recents = recentPeople.map { Person(it) }.filter { matches(it) && !favourites.hasName(it.name) }.take(8)
    val shownContacts = contacts.filter { matches(it) && !favourites.hasName(it.name) }

    FullScreenLayer(onDismiss) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    windowInsets = WindowInsets(0),
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(40) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search or type a name") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true, shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (q.isNotEmpty()) {
                            if (!isSelected(q)) toggle(Person(q))
                            query = ""
                        }
                    }),
                )
                if (selected.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(selected.toList(), key = { it.name }) { p ->
                            InputChip(
                                selected = true, onClick = { selected.remove(p) },
                                label = { Text(p.name) },
                                avatar = { Avatar(p.name, 24.dp) },
                                trailingIcon = { Icon(Icons.Rounded.Close, "Remove", Modifier.width(16.dp)) },
                            )
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    if (q.isNotEmpty() && !isSelected(q)) {
                        item {
                            PersonRow(Person(q), checked = false, subtitle = "Add \"$q\" (not in contacts)", icon = true) {
                                toggle(Person(q)); query = ""
                            }
                        }
                    }
                    if (shownGroups.isNotEmpty()) {
                        item { SectionTitle("Groups") }
                        items(shownGroups, key = { "g${it.group.id}" }) { g ->
                            GroupRow(g, isGroupSelected(g)) { toggleGroup(g) }
                        }
                    }
                    if (shownFavs.isNotEmpty()) {
                        item { SectionTitle("Favourites") }
                        items(shownFavs, key = { "f${it.name}" }) { p ->
                            PersonRow(p, isSelected(p.name), p.phone, favourite = true, onStar = onToggleFavourite?.let { { it(p) } }) { toggle(p) }
                        }
                    }
                    if (recents.isNotEmpty()) {
                        item { SectionTitle("Recent") }
                        items(recents, key = { "r${it.name}" }) { p ->
                            PersonRow(p, isSelected(p.name), "Split with before", onStar = onToggleFavourite?.let { { it(p) } }) { toggle(p) }
                        }
                    }
                    if (!granted) {
                        item {
                            Spacer(Modifier.width(8.dp))
                            AppCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconBubble(Icons.Rounded.Contacts, MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Pick from contacts", fontWeight = FontWeight.SemiBold)
                                        Text("Allow access to choose people from your phone. Or just type a name above.",
                                            style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
                                    }
                                }
                                TextButton(onClick = { launcher.launch(Manifest.permission.READ_CONTACTS) }, modifier = Modifier.align(Alignment.End)) {
                                    Text("Allow")
                                }
                            }
                        }
                    } else if (shownContacts.isNotEmpty()) {
                        item { SectionTitle("Contacts") }
                        items(shownContacts, key = { "c${it.name}" }) { p ->
                            PersonRow(p, isSelected(p.name), p.phone, onStar = onToggleFavourite?.let { { it(p) } }) { toggle(p) }
                        }
                    }
                }
                Button(
                    onClick = { onDone(selected.toList()) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) { Text(if (selected.isEmpty()) "Done" else "Done · ${selected.size} selected") }
            }
        }
    }
}

@Composable
private fun GroupRow(g: GroupWithMembers, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(Icons.Rounded.Groups, MaterialTheme.colorScheme.primary, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(g.group.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                g.members.joinToString(", ") { it.name }.ifEmpty { "No members" },
                style = MaterialTheme.typography.bodySmall, color = extraColors.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(checked = checked, onCheckedChange = { onClick() })
    }
}

@Composable
fun PersonRow(
    p: Person,
    checked: Boolean?,
    subtitle: String,
    icon: Boolean = false,
    favourite: Boolean = false,
    onStar: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) IconBubble(Icons.Rounded.PersonAdd, MaterialTheme.colorScheme.primary, 40.dp) else Avatar(p.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
        }
        if (onStar != null && !icon) {
            IconButton(onClick = onStar) {
                Icon(
                    if (favourite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    if (favourite) "Remove ${p.name} from favourites" else "Add ${p.name} to favourites",
                    tint = if (favourite) MaterialTheme.colorScheme.tertiary else extraColors.subtle,
                )
            }
        }
        if (checked != null) Checkbox(checked = checked, onCheckedChange = { onClick() })
    }
}

/**
 * Full-screen layer drawn on top of the current screen (inside the activity window, so
 * system-bar / keyboard insets are already handled). Back closes it.
 * Used instead of a Dialog window, which was offset below the status bar and pushed
 * the bottom action button off-screen.
 */
@Composable
fun FullScreenLayer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onDismiss)
    content()
}
