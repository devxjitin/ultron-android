package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.AssistantSettings
import com.assistant.app.data.BuiltInSkill
import com.assistant.app.data.GITHUB_SKILL_ID
import com.assistant.app.data.SkillAction
import com.assistant.app.data.SkillPolicy
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/**
 * The assistant's built-in skills, like the tools of an MCP server. You cannot write your own; you can switch a skill on or
 * off, and allow or disallow each of its actions. The assistant first sees only skill names, and learns the allowed actions of
 * a skill only when a request needs it.
 */
@Composable
fun SkillsPage(
    settings: AssistantSettings,
    onSkillToggled: (skillId: String, enabled: Boolean) -> Unit,
    onActionToggled: (actionKey: String, allowed: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val skills = SkillPolicy.catalog(settings)

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text(
            "Skills are built in. Switch a skill off to hide it from the assistant, or open it and turn off single actions, " +
                "for example allow Messages but not Send SMS. Android access for a skill is set up in Connectors.",
            color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 4.dp)
        )
        PageLabel("Skills (${skills.size})")
        skills.forEach { skill ->
            SkillCard(
                skill = skill,
                settings = settings,
                open = openId == skill.id,
                onOpen = { openId = if (openId == skill.id) null else skill.id },
                onSkillToggled = onSkillToggled,
                onActionToggled = onActionToggled
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SkillCard(
    skill: BuiltInSkill,
    settings: AssistantSettings,
    open: Boolean,
    onOpen: () -> Unit,
    onSkillToggled: (String, Boolean) -> Unit,
    onActionToggled: (String, Boolean) -> Unit
) {
    val isGithub = skill.id == GITHUB_SKILL_ID
    val allowed = skill.actions.count { SkillPolicy.isAllowed(settings, it) }
    // GitHub is on while signed in; its sign-in lives in Connectors.
    val on = if (isGithub) settings.githubLogin.isNotEmpty() else allowed > 0
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).background(AssistantSurface)
            .border(BorderStroke(1.dp, AssistantOutline), shape)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(skill.label, color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(skill.description, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                val summary = when {
                    isGithub && !on -> "Sign in from Connectors"
                    !on -> "Off"
                    else -> "$allowed of ${skill.actions.size} actions allowed"
                }
                Text(summary, color = AssistantTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.size(10.dp))
            SkillSwitch(checked = on, enabled = !isGithub, onChange = { onSkillToggled(skill.id, it) })
        }
        if (open) {
            if (skill.actions.isEmpty()) {
                Text("No actions yet.", color = AssistantTextMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
            skill.actions.forEach { action ->
                ActionRow(action, allowed = SkillPolicy.isAllowed(settings, action), enabled = on, onChange = { onActionToggled(action.key, it) })
            }
        }
    }
}

@Composable
private fun ActionRow(action: SkillAction, allowed: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AssistantFieldBackground).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                action.label + if (action.readOnly == false) "  ·  can change data" else "",
                color = AssistantTextPrimary, fontSize = 14.sp
            )
            if (action.hint.isNotBlank()) Text(action.hint, color = AssistantTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.size(10.dp))
        SkillSwitch(checked = allowed, enabled = enabled, onChange = onChange)
    }
}

@Composable
private fun SkillSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = AssistantAccentFill, checkedThumbColor = Color.White,
            uncheckedTrackColor = AssistantFieldBackground, uncheckedBorderColor = AssistantOutline,
            uncheckedThumbColor = AssistantTextMuted
        )
    )
}
