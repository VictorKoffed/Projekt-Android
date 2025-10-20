package com.victorkoffed.projektandroid

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.victorkoffed.projektandroid.data.network.DogApiService
import com.victorkoffed.projektandroid.ui.theme.LabKotlinApiTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LabKotlinApiTheme {
                DogApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DogApp() {
    val context = LocalContext.current
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var breedName by remember { mutableStateOf<String?>(null) }

    // ▼ Dropdown state
    var expanded by remember { mutableStateOf(false) }
    var selectedBreedKey by remember { mutableStateOf<String?>(null) }

    // Sorterad lista (key = api-nyckel, value = svenskt namn)
    val breedItems = remember {
        dogBreedSv.entries.sortedBy { it.value.lowercase(Locale.getDefault()) }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Hundgalleriet",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.2.sp
                            )
                        )
                        Text(
                            "från Dog CEO API",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Pets,
                        contentDescription = "App logo",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(gradient)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            loading = true
                            val handleResult: (String?) -> Unit = { url ->
                                imageUrl = url
                                breedName = url?.let { prettyBreedFromUrl(it) }
                                loading = false
                                if (url == null) {
                                    Toast.makeText(
                                        context,
                                        "Kunde inte hämta bild. Kolla internet och försök igen.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }

                            // ▼ Använd vald ras om finns, annars slump
                            selectedBreedKey?.let {
                                DogApiService.getDogImageByBreedKey(context, it, handleResult)
                            } ?: DogApiService.getRandomDogImage(context, handleResult)
                        },
                        enabled = !loading
                    ) {
                        Text(if (loading) "Hämtar..." else "Hämta hundbild")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ▼ DROPDOWN: välj ras
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedBreedKey?.let { key -> dogBreedSv[key] } ?: "Valfri ras",
                    onValueChange = {},
                    label = { Text("Hundras") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // Null-val: helt slumpmässig
                    DropdownMenuItem(
                        text = { Text("Valfri ras (slump)") },
                        onClick = {
                            selectedBreedKey = null
                            expanded = false
                        }
                    )
                    breedItems.forEach { (key, value) ->
                        DropdownMenuItem(
                            text = { Text(value) },
                            onClick = {
                                selectedBreedKey = key
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (imageUrl == null) {
                Text(
                    "\n\n\n\n\n\n\n\n\nIngen bild ännu.\n\n\n\nVälj gärna en ras och tryck på knappen.\n Annars väljs valfri ras som standard!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Hund av ras: ${breedName ?: "okänd"}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .heightIn(min = 250.dp)
                        )

                        breedName?.let {
                            Text(
                                text = "Ras: $it",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------- Svensk översättning av Dog CEO-raser -----------

private val dogBreedSv = mapOf(
    "affenpinscher" to "Affenpinscher",
    "african" to "Afrikansk jakthund",
    "airedale" to "Airedaleterrier",
    "akita" to "Akita",
    "appenzeller" to "Appenzeller sennenhund",
    "australian-kelpie" to "Australisk kelpie",
    "australian-shepherd" to "Australian shepherd",
    "bakharwal-indian" to "Bakharwal (indisk)",
    "basenji" to "Basenji",
    "beagle" to "Beagle",
    "bluetick" to "Bluetick coonhound",
    "borzoi" to "Borzoi",
    "bouvier" to "Bouvier des Flandres",
    "boxer" to "Boxer",
    "brabancon" to "Petit brabançon",
    "briard" to "Briard",
    "buhund-norwegian" to "Norsk buhund",
    "bulldog-boston" to "Bostonterrier",
    "bulldog-english" to "Engelsk bulldogg",
    "bulldog-french" to "Fransk bulldogg",
    "bullterrier-staffordshire" to "Staffordshire bullterrier",
    "cattledog-australian" to "Australian cattledog",
    "cavapoo" to "Cavapoo",
    "chihuahua" to "Chihuahua",
    "chippiparai-indian" to "Chippiparai (indisk)",
    "chow" to "Chow chow",
    "clumber" to "Clumber spaniel",
    "cockapoo" to "Cockapoo",
    "collie-border" to "Border collie",
    "coonhound" to "Coonhound",
    "corgi-cardigan" to "Welsh corgi cardigan",
    "cotondetulear" to "Coton de Tuléar",
    "dachshund" to "Tax",
    "dalmatian" to "Dalmatiner",
    "dane-great" to "Grand danois",
    "danish-swedish" to "Dansk-svensk gårdshund",
    "deerhound-scottish" to "Skotsk hjorthund",
    "dhole" to "Dhole",
    "dingo" to "Dingo",
    "doberman" to "Dobermann",
    "elkhound-norwegian" to "Norsk älghund",
    "entlebucher" to "Entlebucher sennenhund",
    "eskimo" to "Amerikansk eskimåhund",
    "finnish-lapphund" to "Finsk lapphund",
    "frise-bichon" to "Bichon frisé",
    "gaddi-indian" to "Gaddi (indisk)",
    "germanshepherd" to "Schäfer",
    "greyhound" to "Greyhound",
    "greyhound-indian" to "Indisk greyhound",
    "greyhound-italian" to "Italiensk vinthund",
    "groenendael" to "Belgisk vallhund (Groenendael)",
    "havanese" to "Bichon havanais",
    "hound-afghan" to "Afghanhund",
    "hound-basset" to "Basset hound",
    "hound-blood" to "Blodhund",
    "hound-english" to "Engelsk foxhound",
    "hound-ibizan" to "Ibizahund",
    "hound-plott" to "Plott hound",
    "hound-walker" to "Treeing Walker coonhound",
    "husky" to "Siberian husky",
    "keeshond" to "Keeshond",
    "kelpie" to "Kelpie",
    "kombai" to "Kombai",
    "komondor" to "Komondor",
    "kuvasz" to "Kuvasz",
    "labradoodle" to "Labradoodle",
    "labrador" to "Labrador",
    "leonberg" to "Leonberger",
    "lhasa" to "Lhasa apso",
    "malamute" to "Alaskan malamute",
    "malinois" to "Belgisk vallhund (Malinois)",
    "maltese" to "Malteser",
    "mastiff-bull" to "Bullmastiff",
    "mastiff-english" to "Engelsk mastiff",
    "mastiff-indian" to "Indisk mastiff",
    "mastiff-tibetan" to "Tibetansk mastiff",
    "mexicanhairless" to "Mexikansk nakenhund",
    "mix" to "Blandras",
    "mountain-bernese" to "Berner sennenhund",
    "mountain-swiss" to "Grosser Schweizer sennenhund",
    "mudhol-indian" to "Mudhol hound",
    "newfoundland" to "Newfoundland",
    "otterhound" to "Otterhound",
    "ovcharka-caucasian" to "Kaukasisk ovtjarka",
    "papillon" to "Papillon",
    "pariah-indian" to "Indisk pariahund",
    "pekinese" to "Pekingese",
    "pembroke" to "Welsh corgi pembroke",
    "pinscher-miniature" to "Dvärgpinscher",
    "pitbull" to "Pitbull",
    "pointer-german" to "Tysk pointer",
    "pointer-germanlonghair" to "Tysk långhårig pointer",
    "pomeranian" to "Pomeranian",
    "poodle-medium" to "Mellanpudel",
    "poodle-miniature" to "Dvärgpudel",
    "poodle-standard" to "Standardpudel",
    "poodle-toy" to "Toypudel",
    "pug" to "Mops",
    "puggle" to "Puggle",
    "pyrenees" to "Pyrenéerhund",
    "rajapalayam-indian" to "Rajapalayam (indisk)",
    "redbone" to "Redbone coonhound",
    "retriever-chesapeake" to "Chesapeake bay retriever",
    "retriever-curly" to "Krullpälsad retriever",
    "retriever-flatcoated" to "Flatcoated retriever",
    "retriever-golden" to "Golden retriever",
    "ridgeback-rhodesian" to "Rhodesian ridgeback",
    "rottweiler" to "Rottweiler",
    "saluki" to "Saluki",
    "samoyed" to "Samojed",
    "schipperke" to "Schipperke",
    "schnauzer-giant" to "Riesenschnauzer",
    "schnauzer-miniature" to "Dvärgschnauzer",
    "segugio-italian" to "Italiensk segugio",
    "setter-english" to "Engelsk setter",
    "setter-gordon" to "Gordonsetter",
    "setter-irish" to "Irländsk setter",
    "sharpei" to "Shar pei",
    "sheepdog-english" to "Old English sheepdog",
    "sheepdog-indian" to "Indisk fårhund",
    "sheepdog-shetland" to "Shetland sheepdog",
    "shiba" to "Shiba",
    "shihtzu" to "Shih tzu",
    "spaniel-blenheim" to "Cavalier king charles spaniel",
    "spaniel-brittany" to "Bretagnsk spaniel",
    "spaniel-cocker" to "Cocker spaniel",
    "spaniel-irish" to "Irländsk vattenspaniel",
    "spaniel-japanese" to "Japanese chin",
    "spaniel-sussex" to "Sussex spaniel",
    "spaniel-welsh" to "Welsh springer spaniel",
    "spitz-indian" to "Indisk spets",
    "spitz-japanese" to "Japansk spets",
    "springer-english" to "Engelsk springer spaniel",
    "stbernard" to "Sankt bernhardshund",
    "terrier-american" to "Amerikansk terrier",
    "terrier-australian" to "Australisk terrier",
    "terrier-bedlington" to "Bedlingtonterrier",
    "terrier-border" to "Borderterrier",
    "terrier-cairn" to "Cairnterrier",
    "terrier-dandie" to "Dandie dinmont terrier",
    "terrier-fox" to "Foxterrier",
    "terrier-irish" to "Irländsk terrier",
    "terrier-kerryblue" to "Kerry blue terrier",
    "terrier-lakeland" to "Lakelandterrier",
    "terrier-norfolk" to "Norfolkterrier",
    "terrier-norwich" to "Norwichterrier",
    "terrier-patterdale" to "Patterdale terrier",
    "terrier-russell" to "Jack Russell terrier",
    "terrier-scottish" to "Skotsk terrier",
    "terrier-sealyham" to "Sealyhamterrier",
    "terrier-silky" to "Silkesterrier",
    "terrier-tibetan" to "Tibetansk terrier",
    "terrier-toy" to "Toy terrier",
    "terrier-welsh" to "Welsh terrier",
    "terrier-westhighland" to "West highland white terrier",
    "terrier-wheaten" to "Irländsk softcoated wheaten terrier",
    "terrier-yorkshire" to "Yorkshireterrier",
    "tervuren" to "Belgisk vallhund (Tervueren)",
    "vizsla" to "Vizsla",
    "waterdog-spanish" to "Spansk vattenhund",
    "weimaraner" to "Weimaraner",
    "whippet" to "Whippet",
    "wolfhound-irish" to "Irländsk varghund"
)

// Slår upp direkt i tabellen; annars snygg fallback på engelska
private fun prettyBreedFromUrl(url: String): String {
    val key = Regex("/breeds/([^/]+)/").find(url)?.groupValues?.get(1) ?: return "Okänd ras"
    return dogBreedSv[key] ?: key.replace("-", " ").replaceFirstChar { it.titlecase(Locale.getDefault()) }
}
