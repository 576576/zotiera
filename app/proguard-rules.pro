# 崩溃栈要能对上源码行。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# JNI：原生侧是按字面量找类和方法的（FindClass "com/zerotier/sdk/Node"、
# GetMethodID 连签名一起比对），混淆掉任何一个都会让引擎初始化或回调直接失败。
-keep class com.zerotier.sdk.** { *; }
