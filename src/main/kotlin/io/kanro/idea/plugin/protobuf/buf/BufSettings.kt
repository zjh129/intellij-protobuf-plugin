package io.kanro.idea.plugin.protobuf.buf

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.xmlb.annotations.Attribute

@State(
    name = "BufSettings",
    storages = [Storage("buf.xml")],
)
@Service(Service.Level.PROJECT)
class BufSettings : SimplePersistentStateComponent<BufSettings.State>(State()), ModificationTracker {
    override fun getModificationCount(): Long = stateModificationCount

    class State : BaseState() {
        @get:Attribute
        var bufPath by string("buf")

        @get:Attribute
        var enabled by property(true)
    }

    companion object {
        fun getInstance(project: com.intellij.openapi.project.Project): BufSettings {
            return project.getService(BufSettings::class.java)
        }
    }
}
