/*
 * JNI bridge for accessing the embedded bootstrap ZIP.
 *
 * The ZIP bytes are compiled into the .rodata section of this library
 * by the assembly file (novaterm-bootstrap-zip.S). This function copies
 * them into a Java byte[] that the BootstrapInstaller can process.
 *
 * The function name follows JNI naming convention:
 *   Java_com_novaterm_core_bootstrap_NativeBootstrap_getZip
 *   ^     ^package with underscores                  ^method
 */

#include <jni.h>

/* Defined in novaterm-bootstrap-zip.S */
extern char blob[];
extern int blob_size;

JNIEXPORT jbyteArray JNICALL
Java_com_novaterm_core_bootstrap_NativeBootstrap_getZip(
    JNIEnv *env,
    jclass clazz)
{
    jbyteArray result = (*env)->NewByteArray(env, blob_size);
    if (result == NULL) {
        return NULL; /* OutOfMemoryError already thrown */
    }
    (*env)->SetByteArrayRegion(env, result, 0, blob_size, (jbyte *)blob);
    return result;
}
