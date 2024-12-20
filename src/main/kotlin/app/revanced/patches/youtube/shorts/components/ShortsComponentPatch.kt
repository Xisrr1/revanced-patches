package app.revanced.patches.youtube.shorts.components

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.shared.litho.LithoFilterPatch
import app.revanced.patches.shared.textcomponent.TextComponentPatch
import app.revanced.patches.youtube.shorts.components.fingerprints.ShortsButtonFingerprint
import app.revanced.patches.youtube.shorts.components.fingerprints.ShortsPaidPromotionFingerprint
import app.revanced.patches.youtube.shorts.components.fingerprints.ShortsPausedHeaderFingerprint
import app.revanced.patches.youtube.shorts.components.fingerprints.ShortsPivotLegacyFingerprint
import app.revanced.patches.youtube.shorts.components.fingerprints.ShortsSubscriptionsTabletFingerprint
import app.revanced.patches.youtube.shorts.components.fingerprints.ShortsSubscriptionsTabletParentFingerprint
import app.revanced.patches.youtube.utils.compatibility.Constants.COMPATIBLE_PACKAGE
import app.revanced.patches.youtube.utils.integrations.Constants.COMPONENTS_PATH
import app.revanced.patches.youtube.utils.integrations.Constants.SHORTS_CLASS_DESCRIPTOR
import app.revanced.patches.youtube.utils.integrations.Constants.UTILS_PATH
import app.revanced.patches.youtube.utils.playertype.PlayerTypeHookPatch
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.ReelDynRemix
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.ReelDynShare
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.ReelForcedMuteButton
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.ReelPlayerFooter
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.ReelPlayerRightPivotV2Size
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.ReelRightDislikeIcon
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.ReelRightLikeIcon
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.RightComment
import app.revanced.patches.youtube.utils.settings.SettingsPatch
import app.revanced.patches.youtube.video.information.VideoInformationPatch
import app.revanced.util.REGISTER_TEMPLATE_REPLACEMENT
import app.revanced.util.getWalkerMethod
import app.revanced.util.indexOfFirstInstructionOrThrow
import app.revanced.util.indexOfFirstInstructionReversedOrThrow
import app.revanced.util.indexOfFirstWideLiteralInstructionValueOrThrow
import app.revanced.util.patch.BaseBytecodePatch
import app.revanced.util.replaceLiteralInstructionCall
import app.revanced.util.resultOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
object ShortsComponentPatch : BaseBytecodePatch(
    name = "Shorts components",
    description = "Adds options to hide or change components related to YouTube Shorts.",
    dependencies = setOf(
        LithoFilterPatch::class,
        PlayerTypeHookPatch::class,
        SettingsPatch::class,
        SharedResourceIdPatch::class,
        ShortsAnimationPatch::class,
        ShortsNavigationBarPatch::class,
        ShortsRepeatPatch::class,
        ShortsTimeStampPatch::class,
        ShortsToolBarPatch::class,
        TextComponentPatch::class,
        VideoInformationPatch::class,
    ),
    compatiblePackages = COMPATIBLE_PACKAGE,
    fingerprints = setOf(
        ShortsButtonFingerprint,
        ShortsPaidPromotionFingerprint,
        ShortsPausedHeaderFingerprint,
        ShortsPivotLegacyFingerprint,
        ShortsSubscriptionsTabletParentFingerprint,
    )
) {
    private const val INTEGRATION_CLASS_DESCRIPTOR =
        "$UTILS_PATH/ReturnYouTubeChannelNamePatch;"

    private const val BUTTON_FILTER_CLASS_DESCRIPTOR =
        "$COMPONENTS_PATH/ShortsButtonFilter;"
    private const val SHELF_FILTER_CLASS_DESCRIPTOR =
        "$COMPONENTS_PATH/ShortsShelfFilter;"
    private const val RETURN_YOUTUBE_CHANNEL_NAME_FILTER_CLASS_DESCRIPTOR =
        "$COMPONENTS_PATH/ReturnYouTubeChannelNameFilterPatch;"

    override fun execute(context: BytecodeContext) {

        var settingArray = arrayOf(
            "PREFERENCE_SCREEN: SHORTS",
            "SETTINGS: SHORTS_COMPONENTS"
        )

        if (SettingsPatch.upward1925 && !SettingsPatch.upward1928) {
            settingArray += "SETTINGS: SHORTS_TIME_STAMP"
        }

        // region patch for hide comments button (non-litho)

        ShortsButtonFingerprint.hideButton(RightComment, "hideShortsCommentsButton", false)

        // endregion

        // region patch for hide dislike button (non-litho)

        ShortsButtonFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val constIndex =
                    indexOfFirstWideLiteralInstructionValueOrThrow(ReelRightDislikeIcon)
                val constRegister = getInstruction<OneRegisterInstruction>(constIndex).registerA

                val jumpIndex = indexOfFirstInstructionOrThrow(constIndex, Opcode.CONST_CLASS) + 2

                addInstructionsWithLabels(
                    constIndex + 1, """
                        invoke-static {}, $SHORTS_CLASS_DESCRIPTOR->hideShortsDislikeButton()Z
                        move-result v$constRegister
                        if-nez v$constRegister, :hide
                        const v$constRegister, $ReelRightDislikeIcon
                        """, ExternalLabel("hide", getInstruction(jumpIndex))
                )
            }
        }

        // endregion

        // region patch for hide like button (non-litho)

        ShortsButtonFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val insertIndex = indexOfFirstWideLiteralInstructionValueOrThrow(ReelRightLikeIcon)
                val insertRegister = getInstruction<OneRegisterInstruction>(insertIndex).registerA
                val jumpIndex = indexOfFirstInstructionOrThrow(insertIndex, Opcode.CONST_CLASS) + 2

                addInstructionsWithLabels(
                    insertIndex + 1, """
                        invoke-static {}, $SHORTS_CLASS_DESCRIPTOR->hideShortsLikeButton()Z
                        move-result v$insertRegister
                        if-nez v$insertRegister, :hide
                        const v$insertRegister, $ReelRightLikeIcon
                        """, ExternalLabel("hide", getInstruction(jumpIndex))
                )
            }
        }

        // endregion

        // region patch for hide sound button

        val shortsPivotLegacyFingerprintResult = ShortsPivotLegacyFingerprint.result

        if (shortsPivotLegacyFingerprintResult != null) {
            // Legacy method.
            shortsPivotLegacyFingerprintResult.mutableMethod.apply {
                val targetIndex =
                    indexOfFirstWideLiteralInstructionValueOrThrow(ReelForcedMuteButton)
                val targetRegister = getInstruction<OneRegisterInstruction>(targetIndex).registerA

                val insertIndex = indexOfFirstInstructionReversedOrThrow(targetIndex, Opcode.IF_EQZ)
                val jumpIndex = indexOfFirstInstructionOrThrow(targetIndex, Opcode.GOTO)

                addInstructionsWithLabels(
                    insertIndex, """
                        invoke-static {}, $SHORTS_CLASS_DESCRIPTOR->hideShortsSoundButton()Z
                        move-result v$targetRegister
                        if-nez v$targetRegister, :hide
                        """, ExternalLabel("hide", getInstruction(jumpIndex))
                )
            }
        } else if (ReelPlayerRightPivotV2Size != -1L) {
            // Invoke Sound button dimen into integrations.
            val smaliInstruction = """
                invoke-static {v$REGISTER_TEMPLATE_REPLACEMENT}, $SHORTS_CLASS_DESCRIPTOR->getShortsSoundButtonDimenId(I)I
                move-result v$REGISTER_TEMPLATE_REPLACEMENT
                """

            context.replaceLiteralInstructionCall(
                ReelPlayerRightPivotV2Size,
                smaliInstruction
            )
        } else {
            throw PatchException("ReelPlayerRightPivotV2Size is not found")
        }

        // endregion

        // region patch for hide remix button (non-litho)

        ShortsButtonFingerprint.hideButton(ReelDynRemix, "hideShortsRemixButton", true)

        // endregion

        // region patch for hide share button (non-litho)

        ShortsButtonFingerprint.hideButton(ReelDynShare, "hideShortsShareButton", true)

        // endregion

        // region patch for hide paid promotion label (non-litho)

        ShortsPaidPromotionFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                when (returnType) {
                    "Landroid/widget/TextView;" -> {
                        val insertIndex = implementation!!.instructions.size - 1
                        val insertRegister =
                            getInstruction<OneRegisterInstruction>(insertIndex).registerA

                        addInstructions(
                            insertIndex + 1, """
                                invoke-static {v$insertRegister}, $SHORTS_CLASS_DESCRIPTOR->hideShortsPaidPromotionLabel(Landroid/widget/TextView;)V
                                return-object v$insertRegister
                                """
                        )
                        removeInstruction(insertIndex)
                    }

                    "V" -> {
                        addInstructionsWithLabels(
                            0, """
                                invoke-static {}, $SHORTS_CLASS_DESCRIPTOR->hideShortsPaidPromotionLabel()Z
                                move-result v0
                                if-eqz v0, :show
                                return-void
                                """, ExternalLabel("show", getInstruction(0))
                        )
                    }

                    else -> {
                        throw PatchException("Unknown returnType: $returnType")
                    }
                }
            }
        }

        // endregion

        // region patch for hide subscribe button (non-litho)

        // This method is deprecated since YouTube v18.31.xx.
        if (!SettingsPatch.upward1831) {
            ShortsSubscriptionsTabletParentFingerprint.resultOrThrow().let { parentResult ->
                lateinit var subscriptionFieldReference: FieldReference

                parentResult.mutableMethod.apply {
                    val targetIndex =
                        indexOfFirstWideLiteralInstructionValueOrThrow(ReelPlayerFooter) - 1
                    subscriptionFieldReference =
                        (getInstruction<ReferenceInstruction>(targetIndex)).reference as FieldReference
                }

                ShortsSubscriptionsTabletFingerprint.also {
                    it.resolve(
                        context,
                        parentResult.classDef
                    )
                }.resultOrThrow().mutableMethod.apply {
                    implementation!!.instructions.filter { instruction ->
                        val fieldReference =
                            (instruction as? ReferenceInstruction)?.reference as? FieldReference
                        instruction.opcode == Opcode.IGET
                                && fieldReference == subscriptionFieldReference
                    }.forEach { instruction ->
                        val insertIndex = implementation!!.instructions.indexOf(instruction) + 1
                        val register = (instruction as TwoRegisterInstruction).registerA

                        addInstructions(
                            insertIndex, """
                                invoke-static {v$register}, $SHORTS_CLASS_DESCRIPTOR->hideShortsSubscribeButton(I)I
                                move-result v$register
                                """
                        )
                    }
                }
            }
        }

        // endregion

        // region patch for hide paused header

        ShortsPausedHeaderFingerprint.resultOrThrow().let {
            it.mutableMethod.apply {
                val targetIndex = it.scanResult.patternScanResult!!.endIndex + 1
                val targetInstruction = getInstruction(targetIndex)
                val targetReference =
                    (targetInstruction as? ReferenceInstruction)?.reference as? MethodReference
                val useMethodWalker = targetInstruction.opcode == Opcode.INVOKE_VIRTUAL &&
                        targetReference?.returnType == "V" &&
                        targetReference.parameterTypes.firstOrNull() == "Landroid/view/View;"

                if (useMethodWalker) {
                    // YouTube 18.29.38 ~ YouTube 19.28.42
                    getWalkerMethod(context, targetIndex).apply {
                        addInstructionsWithLabels(
                            0, """
                                invoke-static {}, $SHORTS_CLASS_DESCRIPTOR->hideShortsPausedHeader()Z
                                move-result v0
                                if-eqz v0, :show
                                return-void
                                """, ExternalLabel("show", getInstruction(0))
                        )
                    }
                } else {
                    // YouTube 19.29.42 ~
                    val insertIndex = it.scanResult.patternScanResult!!.startIndex
                    val insertRegister =
                        getInstruction<OneRegisterInstruction>(insertIndex).registerA

                    addInstructions(
                        insertIndex, """
                            invoke-static {v$insertRegister}, $SHORTS_CLASS_DESCRIPTOR->hideShortsPausedHeader(Z)Z
                            move-result v$insertRegister
                            """
                    )
                }
            }
        }

        // endregion

        // region patch for return shorts channel name

        TextComponentPatch.hookSpannableString(INTEGRATION_CLASS_DESCRIPTOR, "onCharSequenceLoaded")

        VideoInformationPatch.hookShorts("$INTEGRATION_CLASS_DESCRIPTOR->newShortsVideoStarted(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JZ)V")

        // endregion

        LithoFilterPatch.addFilter(BUTTON_FILTER_CLASS_DESCRIPTOR)
        LithoFilterPatch.addFilter(SHELF_FILTER_CLASS_DESCRIPTOR)
        LithoFilterPatch.addFilter(RETURN_YOUTUBE_CHANNEL_NAME_FILTER_CLASS_DESCRIPTOR)

        /**
         * Add settings
         */
        SettingsPatch.addPreference(settingArray)

        SettingsPatch.updatePatchStatus(this)
    }

    private fun MethodFingerprint.hideButton(
        id: Long,
        descriptor: String,
        reversed: Boolean
    ) {
        resultOrThrow().let {
            it.mutableMethod.apply {
                val constIndex = indexOfFirstWideLiteralInstructionValueOrThrow(id)
                val insertIndex = if (reversed)
                    indexOfFirstInstructionReversedOrThrow(constIndex, Opcode.CHECK_CAST)
                else
                    indexOfFirstInstructionOrThrow(constIndex, Opcode.CHECK_CAST)
                val insertRegister = getInstruction<OneRegisterInstruction>(insertIndex).registerA

                addInstruction(
                    insertIndex,
                    "invoke-static {v$insertRegister}, $SHORTS_CLASS_DESCRIPTOR->$descriptor(Landroid/view/View;)V"
                )
            }
        }
    }

    private fun MethodFingerprint.hideButtons(
        id: Long,
        descriptor: String
    ) {
        resultOrThrow().let {
            it.mutableMethod.apply {
                val constIndex = indexOfFirstWideLiteralInstructionValueOrThrow(id)
                val insertIndex = indexOfFirstInstructionOrThrow(constIndex, Opcode.CHECK_CAST)

                hideButtons(insertIndex, descriptor)
            }
        }
    }

    private fun MutableMethod.hideButtons(
        insertIndex: Int,
        descriptor: String
    ) {
        val insertRegister = getInstruction<OneRegisterInstruction>(insertIndex).registerA

        addInstructions(
            insertIndex + 1, """
                invoke-static {v$insertRegister}, $SHORTS_CLASS_DESCRIPTOR->$descriptor
                move-result-object v$insertRegister
                """
        )
    }
}
