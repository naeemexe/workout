# R8 rules for release builds. Room, Firebase and coroutines ship their own rules; these cover the rest.

# Credential Manager finds its Google Play services provider by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# Keep line numbers so Play Console crash reports are readable (upload the mapping file with each release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
