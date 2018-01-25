package com.github.izhangzhihao.rainbow.brackets

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.BracePair
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlFile
import com.intellij.util.containers.Stack
import com.intellij.util.containers.isNullOrEmpty

/**
 * DefaultRainbowVisitor
 *
 * Created by Yii.Guxing on 2018/1/23
 */
class DefaultRainbowVisitor : RainbowHighlightVisitor() {

    private var bracePairs: List<BracePair>? = null

    private val stackMap: MutableMap<BracePair, Stack<PsiElement>> = mutableMapOf()

    private var checkLanguage: Boolean = false

    override fun suitableForFile(file: PsiFile): Boolean = file is XmlFile ||
            !file.language.bracePairs.isNullOrEmpty() ||
            file.viewProvider.allFiles.any { !it.language.bracePairs.isNullOrEmpty() }

    override fun clone(): HighlightVisitor = DefaultRainbowVisitor()

    override fun onBeforeAnalyze(file: PsiFile, updateWholeFile: Boolean) {
        checkLanguage = file is XmlFile
        bracePairs = file.language.bracePairs
    }

    override fun onAfterAnalyze() {
        super.onAfterAnalyze()
        bracePairs = null
        checkLanguage = false
        stackMap.clear()
    }

    override fun visit(element: PsiElement) {
        if (element is XmlElement) {
            return
        }

        val type = element.node?.elementType ?: return
        val pairs = bracePairs ?: element.language.takeIf { checkLanguage }?.bracePairs ?: return
        val pair = pairs.find { it.leftBraceType == type || it.rightBraceType == type } ?: return

        if (pair.leftBraceType == type) {
            val stack = stackMap[pair] ?: Stack<PsiElement>().also { stackMap[pair] = it }
            if (stack.isNotEmpty()) {
                val headerParent = stack.first().parent
                if (PsiTreeUtil.findFirstParent(element, true) { it == headerParent } == null) {
                    stack.clear()
                }
            }

            stack.push(element)
        } else {
            stackMap[pair]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { stack ->
                        val headerParent = stack.first().parent
                        element.level(headerParent)?.let {
                            stack.pop().addHighlightInfo(it)
                            element.addHighlightInfo(it)
                        }
                    }
        }
    }

    companion object {
        private tailrec fun iterateParents(element: PsiElement?, parent: PsiElement, count: Int = 0): Int? {
            if (element == parent) {
                return count
            }

            if (element == null || element is PsiFile) {
                return null
            }

            return iterateParents(element.parent, parent, count + 1)
        }

        private fun PsiElement.level(parent: PsiElement): Int? = iterateParents(this.parent, parent)
    }
}