package com.studyapp.util

/**
 * 密码强度策略（高复杂度）：12~18 位，须含小写字母、大写字母与符号。
 * 与后端 server.js passwordComplexityError 保持一致。
 */
object PasswordPolicy {

    const val MIN_LEN = 12
    const val MAX_LEN = 18

    private const val SYMBOLS = """!@#$%^&*()_+-=[]{}|;:',.<>/?`~"""

    /**
     * @return 符合要求返回 null；否则返回给用户看的提示文案
     */
    fun check(password: String): String? {
        val p = password.orEmpty()
        return when {
            p.length < MIN_LEN || p.length > MAX_LEN -> "密码需为12~18位"
            p.none { it in 'a'..'z' } -> "密码需包含小写字母"
            p.none { it in 'A'..'Z' } -> "密码需包含大写字母"
            p.none { it in SYMBOLS } -> "密码需包含符号（如 @ # ! 等）"
            else -> null
        }
    }
}
