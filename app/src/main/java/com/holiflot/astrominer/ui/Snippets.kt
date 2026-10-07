package com.holiflot.astrominer.ui

import com.holiflot.astrominer.GameUiState
import com.holiflot.astrominer.core.game.Commands
import com.holiflot.astrominer.core.lang.Feature

/**
 * Hızlı ekleme çubuğundaki parçalar. Sadece açılmış komutlar ve dil özellikleri görünür,
 * böylece çubuk oyuncunun ilerlemesiyle birlikte büyür.
 */
fun quickSnippets(state: GameUiState): List<String> {
    val commands = Commands.all
        .filter { it.unlockId == null || it.unlockId in state.unlockedIds }
        .filterNot { it.name == "print" }
        .map { it.snippet }

    val keywords = buildList {
        if (Feature.WHILE in state.features) add("while True:")
        if (Feature.IF in state.features) addAll(listOf("if ", "elif ", "else:"))
        if (Feature.FOR in state.features) add("for i in range():")
        if (Feature.FUNCTIONS in state.features) addAll(listOf("def ():", "return "))
        if (Feature.OPERATORS in state.features) addAll(listOf("not ", "and ", "or ", "==", "!=", "<", ">", "+", "-", "%"))
        if (Feature.VARIABLES in state.features) add(" = ")
        if (Feature.LISTS in state.features) addAll(listOf("[]", ".append()", "len()"))
    }

    return commands + keywords + listOf("(", ")", ":", "print()", "#")
}
