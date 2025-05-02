package com.yourorg.openapi

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions

class OpenApiFlattenerPlugin implements Plugin<Project> {
    void apply(Project project) {
        def extension = project.extensions.create("openapiFlatten", OpenApiFlattenerExtension)

        project.tasks.register("flattenOpenApiSpec", DefaultTask) {
            group = "openapi"
            description = "Flatten OpenAPI spec by resolving external \$ref aliases."

            inputs.file { extension.inputSpec }
            outputs.file { extension.outputSpec }

            doLast {
                def yaml = new Yaml()
                def inputFile = extension.inputSpec
                def outputFile = extension.outputSpec
                def aliasPaths = extension.aliases

                def input = yaml.load(inputFile.text)
                def loadedSources = aliasPaths.collectEntries { alias, path ->
                    [(alias): yaml.load(project.file(path).text)]
                }

                def usedSchemas = [:]

                def replaceRefs
                replaceRefs = { node ->
                    if (node instanceof Map) {
                        def keysToRemove = []
                        def newEntries = [:]
                        node.each { k, v ->
                            if (k == '$ref' && v instanceof String) {
                                def mSchema = v =~ /@?(Models|Dictionary|Enumerations)#\/components\/schemas\/(\w+)/
                                def mDefn = v =~ /@?Dictionary#\/definitions\/(\w+)/

                                if (mSchema.matches()) {
                                    def alias = mSchema[0][1]
                                    def name = mSchema[0][2]
                                    def src = loadedSources[alias]
                                    def schema = src?.components?.schemas?.get(name)
                                    if (schema) {
                                        usedSchemas[name] = schema
                                        keysToRemove << '$ref'
                                        newEntries['$ref'] = "#/components/schemas/${name}".toString()
                                    }
                                } else if (mDefn.matches()) {
                                    def name = mDefn[0][1]
                                    def defn = loadedSources.Dictionary?.definitions?.get(name)
                                    if (defn) {
                                        keysToRemove << '$ref'
                                        defn.each { key, value -> newEntries[key.toString()] = value }
                                    }
                                }
                            } else {
                                replaceRefs(v)
                            }
                        }
                        keysToRemove.each { node.remove(it) }
                        newEntries.each { k, v -> node[k] = v }
                    } else if (node instanceof List) {
                        node.each { replaceRefs(it) }
                    }
                }

                replaceRefs(input)

                input.components = input.components ?: [:]
                input.components.schemas = input.components.schemas ?: [:]
                input.components.schemas.putAll(usedSchemas)
                usedSchemas.values().collect().each { replaceRefs(it) }

                def options = new DumperOptions()
                options.setPrettyFlow(true)
                options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
                def outYaml = new Yaml(options)

                outputFile.parentFile.mkdirs()
                outputFile.text = outYaml.dump(input)

                println "✅ Flattened OpenAPI written to: ${outputFile}"
            }
        }
    }
}

class OpenApiFlattenerExtension {
    File inputSpec
    File outputSpec
    Map<String, String> aliases = [:]
}
