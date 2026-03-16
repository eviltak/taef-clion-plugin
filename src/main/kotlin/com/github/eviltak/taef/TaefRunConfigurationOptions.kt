package com.github.eviltak.taef

import com.intellij.execution.configurations.LocatableRunConfigurationOptions

class TaefRunConfigurationOptions : LocatableRunConfigurationOptions() {
    var testDllPath by string("")
    var teExePath by string("")
    var nameFilter by string("")
    var selectQuery by string("")
    var workingDirectory by string("")
    var inproc by property(false)
    var additionalArgs by string("")
    var cmakeTarget by string("")
}
