/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.test.fixtures.ivy

import groovy.xml.MarkupBuilder
import org.gradle.api.Action
import org.gradle.api.attributes.Usage
import org.gradle.internal.xml.XmlTransformer
import org.gradle.test.fixtures.AbstractModule
import org.gradle.test.fixtures.GradleModuleMetadata
import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.ModuleArtifact
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.gradle.DependencyConstraintSpec
import org.gradle.test.fixtures.gradle.DependencySpec
import org.gradle.test.fixtures.gradle.GradleFileModuleAdapter
import org.gradle.test.fixtures.gradle.VariantMetadata

class IvyFileModule extends AbstractModule implements IvyModule {
    final String ivyPattern
    final String artifactPattern
    final TestFile moduleDir
    final String organisation
    final String module
    final String revision
    final boolean m2Compatible
    final List dependencies = []
    final List dependencyConstraints = []
    final Map<String, Map> configurations = [:]
    final List artifacts = []
    final Map extendsFrom = [:]
    final Map extraAttributes = [:]
    final Map extraInfo = [:]
    private final List<VariantMetadata> variants = [new VariantMetadata("api", [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_API]), new VariantMetadata("runtime", [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_RUNTIME])]
    String branch = null
    String status = "integration"
    MetadataPublish metadataPublish = MetadataPublish.ALL

    int publishCount = 1
    XmlTransformer transformer = new XmlTransformer()
    private final String modulePath

    // cached to improve performance of tests
    GradleModuleMetadata parsedModuleMetadata

    enum MetadataPublish {
        ALL(true, true),
        IVY(true, false),
        GRADLE(false, true),
        NONE(false, false)

        private final boolean ivy
        private final boolean gradle
        MetadataPublish(boolean ivy, boolean gradle) {
            this.ivy = ivy
            this.gradle = gradle
        }

        boolean publishIvy() {
            ivy
        }

        boolean publishGradle() {
            gradle
        }
    }

    IvyFileModule(String ivyPattern, String artifactPattern, String modulePath, TestFile moduleDir, String organisation, String module, String revision, boolean m2Compatible) {
        this.modulePath = modulePath
        this.ivyPattern = ivyPattern
        this.artifactPattern = artifactPattern
        this.moduleDir = moduleDir
        this.organisation = organisation
        this.module = module
        this.revision = revision
        this.m2Compatible = m2Compatible
        configurations['runtime'] = [extendsFrom: [], transitive: true, visibility: 'public']
        configurations['default'] = [extendsFrom: ['runtime'], transitive: true, visibility: 'public']
    }

    @Override
    String getGroup() {
        return organisation
    }

    @Override
    String getVersion() {
        return revision
    }

    IvyDescriptor getParsedIvy() {
        return new IvyDescriptor(ivyFile)
    }

    IvyFileModule configuration(Map<String, ?> options = [:], String name) {
        configurations[name] = [extendsFrom: options.extendsFrom ?: [], transitive: options.transitive ?: true, visibility: options.visibility ?: 'public']
        return this
    }

    @Override
    IvyFileModule variant(String variant, Map<String, String> attributes) {
        variants.add(new VariantMetadata(variant, attributes))
        configuration(variant) //add variant also as configuration for plain ivy publishing
        return this
    }

    IvyFileModule withXml(Closure action) {
        transformer.addAction(action);
        return this
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of name, type, ext, classifier, conf
     * @return this
     */
    IvyFileModule artifact(Map<String, ?> options = [:]) {
        artifacts << toArtifact(options)
        return this
    }

    IvyFileModule undeclaredArtifact(Map<String, ?> options) {
        def undeclaredArtifact = toArtifact(options) + [undeclared: true]
        artifacts << undeclaredArtifact
        return this
    }

    Map<String, ?> toArtifact(Map<String, ?> options = [:]) {
        def type = notNullOr(options.type, 'jar')
        return [name: options.name ?: module, type: type,
                ext: notNullOr(options.ext, type), classifier: options.classifier ?: null, conf: options.conf ?: '*']
    }

    def notNullOr(def value, def defaultValue) {
        if (value != null) {
            return value
        }
        return defaultValue
    }

    IvyFileModule dependsOn(String organisation, String module, String revision) {
        dependsOn([organisation: organisation, module: module, revision: revision])
        return this
    }

    @Override
    IvyFileModule dependsOn(Map<String, ?> attributes, Module target) {
        def allAttrs = [organisation: target.group, module: target.module, revision: target.version]
        allAttrs.putAll(attributes)
        dependsOn(allAttrs)
        return this
    }

    @Override
    IvyModule dependencyConstraint(Module target) {
        dependencyConstraints << [organisation: target.group, module: target.module, revision: target.version]
        return this
    }

    @Override
    IvyModule dependencyConstraint(Map<String, ?> attributes, Module module) {
        def allAttrs = [organisation: module.group, module: module.module, revision: module.version]
        allAttrs.putAll(attributes)
        dependencyConstraints << allAttrs
        return this
    }

    IvyFileModule dependsOn(Map<String, ?> attributes) {
        dependencies << attributes
        return this
    }

    @Override
    IvyFileModule dependsOn(Module target) {
        dependsOn(target.group, target.module, target.version)
        return this
    }

    IvyFileModule dependsOn(String... modules) {
        modules.each { dependsOn(organisation, it, revision) }
        return this
    }

    IvyFileModule extendsFrom(Map<String, ?> attributes) {
        this.extendsFrom.clear()
        this.extendsFrom.putAll(attributes)
        return this
    }

    IvyFileModule nonTransitive(String config) {
        configurations[config].transitive = false
        return this
    }

    IvyFileModule withStatus(String status) {
        this.status = status
        return this
    }

    IvyFileModule withBranch(String branch) {
        this.branch = branch
        return this
    }

    IvyFileModule withNoMetaData() {
        metadataPublish = MetadataPublish.NONE
        return this
    }

    @Override
    IvyModule withNoIvyMetaData() {
        metadataPublish = MetadataPublish.GRADLE
        return this
    }

    IvyFileModule withExtraAttributes(Map extraAttributes) {
        this.extraAttributes.putAll(extraAttributes)
        return this
    }

    /**
     * Keys in extra info will be prefixed with namespace prefix "ns" in this fixture.
     */
    IvyFileModule withExtraInfo(Map extraInfo) {
        this.extraInfo.putAll(extraInfo)
        return this
    }

    @Override
    ModuleArtifact getIvy() {
        return moduleArtifact([name: "ivy", type: "ivy", ext: "xml"], ivyPattern)
    }

    @Override
    ModuleArtifact getModuleMetadata() {
        moduleArtifact(name: module, type: 'module', ext: "module")
    }

    @Override
    GradleModuleMetadata getParsedModuleMetadata() {
        if (parsedModuleMetadata == null) {
            parsedModuleMetadata = new GradleModuleMetadata(moduleMetadataFile)
        }
        parsedModuleMetadata
    }

    TestFile getIvyFile() {
        return ivy.file
    }

    @Override
    ModuleArtifact getJar() {
        return moduleArtifact(name: module, type: "jar", ext: "jar")
    }

    TestFile getJarFile() {
        return jar.file
    }

    @Override
    TestFile getModuleMetadataFile() {
        return moduleMetadata.file
    }

    TestFile file(Map<String, ?> options) {
        return moduleArtifact(options).file
    }

    IvyModuleArtifact moduleArtifact(Map<String, ?> options, String pattern = artifactPattern) {
        def path = getArtifactFilePath(options, pattern)
        def file = moduleDir.file(path)
        return new IvyModuleArtifact() {
            @Override
            String getPath() {
                return modulePath + '/' + path
            }

            @Override
            TestFile getFile() {
                return file
            }

            @Override
            Map<String, String> getIvyTokens() {
                toTokens(options)
            }
        }
    }

    protected String getArtifactFilePath(Map<String, ?> options, String pattern = artifactPattern) {
        LinkedHashMap<String, Object> tokens = toTokens(options)
        M2CompatibleIvyPatternHelper.substitute(pattern, m2Compatible, tokens)
    }

    private LinkedHashMap<String, String> toTokens(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def tokens = [organisation: organisation, module: module, revision: revision, artifact: artifact.name, type: artifact.type, ext: artifact.ext, classifier: artifact.classifier]
        tokens
    }

    /**
     * Publishes ivy.xml plus all artifacts with different content to previous publication.
     */
    IvyFileModule publishWithChangedContent() {
        publishCount++
        publish()
    }

    String getPublicationDate() {
        return String.format("2010010112%04d", publishCount)
    }

    /**
     * Publishes ivy.xml (if enabled) plus all artifacts
     */
    IvyFileModule publish() {
        moduleDir.createDir()

        if (artifacts.empty) {
            artifact([:])
        }

        artifacts.each { artifact ->
            def artifactFile = file(artifact)
            publish(artifactFile) { Writer writer ->
                writer << "${artifactFile.name} : $artifactContent"
            }
        }


        if (metadataPublish == MetadataPublish.NONE) {
            return this
        }

        if (hasModuleMetadata && metadataPublish.publishGradle()) {
            publishModuleMetadata()
        }

        if (metadataPublish.publishIvy()) {
            publish(ivyFile) { Writer writer ->
                transformer.transform(writer, { writeTo(it) } as Action)
            }
        }

        return this
    }

    private void publishModuleMetadata() {
        GradleFileModuleAdapter adapter = new GradleFileModuleAdapter(organisation, module, revision,
            variants.collect { v ->
                new VariantMetadata(
                    v.name,
                    v.attributes,
                    dependencies.collect { d ->
                        new DependencySpec(d.organisation, d.module, d.revision, d.rejects, d.exclusions)
                    },
                    dependencyConstraints.collect { d ->
                        new DependencyConstraintSpec(d.organisation, d.module, d.revision, d.rejects)
                    },
                    artifacts.collect { moduleArtifact(it) }
                )
            },
            ['org.gradle.status': status]
        )

        adapter.publishTo(moduleDir)
    }


    private writeTo(Writer ivyFileWriter) {
        ivyFileWriter << """<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="1.0" xmlns:m="http://ant.apache.org/ivy/maven" """
        if (extraAttributes) {
            ivyFileWriter << ' xmlns:e="http://ant.apache.org/ivy/extra"'
        }
        ivyFileWriter << "><!--" + artifactContent + "-->"

        def builder = new MarkupBuilder(ivyFileWriter)
        def infoAttrs = [organisation: organisation, module: module, revision: revision, status: status, publication: getPublicationDate()]
        if (branch) {
            infoAttrs.branch = branch
        }
        infoAttrs += extraAttributes.collectEntries {key, value -> ["e:$key", value]}
        builder.info(infoAttrs) {
            if (extendsFrom) {
                "extends"(extendsFrom)
            }
            extraInfo.each { key, value ->
                "ns:${key.name}"('xmlns:ns': "${key.namespace}", value)
            }
        }
        builder.configurations {
            configurations.each { name, config ->
                def confAttrs = [name: name, visibility: config.visibility]
                if (config.extendsFrom) {
                    confAttrs.extends=config.extendsFrom.join(',')
                }
                if (!config.transitive) {
                    confAttrs.transitive='false'
                }
                conf(confAttrs)
            }
        }
        builder.publications {
            artifacts.each { art ->
                if (!art.undeclared) {
                    def attrs = [name: art.name, type:art.type, ext: art.ext, conf:art.conf]
                    if (art.classifier) {
                        attrs["m:classifier"] = art.classifier
                    }
                    builder.artifact(attrs)
                }
            }
        }
        builder.dependencies {
            dependencies.each { dep ->
                def depAttrs = [org: dep.organisation, name: dep.module, rev: dep.revision]
                if (dep.conf) {
                    depAttrs.conf = dep.conf
                }
                if (dep.revConstraint) {
                    depAttrs.revConstraint = dep.revConstraint
                }
                builder.dependency(depAttrs) {
                    if (dep.exclusions) {
                        for (exc in dep.exclusions) {
                            def excludeAttrs = [:]
                            if (exc.group) {
                                excludeAttrs.org = exc.group
                            }
                            if (exc.module) {
                                excludeAttrs.module = exc.module
                            }
                            builder.exclude(excludeAttrs)
                        }
                    }
                }
            }
        }

ivyFileWriter << '</ivy-module>'
    }

    @Override
    protected onPublish(TestFile file) {
        sha1File(file)
    }

    private String getArtifactContent() {
        // Some content to include in each artifact, so that its size and content varies on each publish
        return (0..publishCount).join("-")
    }

    /**
     * Asserts that exactly the given artifacts have been published.
     */
    void assertArtifactsPublished(String... names) {
        def expectedArtifacts = [] as Set
        for (name in names) {
            expectedArtifacts.addAll([name, "${name}.sha1"])
        }

        List<String> publishedArtifacts = moduleDir.list().sort()
        expectedArtifacts = (expectedArtifacts as List).sort()
        assert publishedArtifacts == expectedArtifacts
        for (name in names) {
            assertChecksumPublishedFor(moduleDir.file(name))
        }
    }

    void assertChecksumPublishedFor(TestFile testFile) {
        def sha1File = sha1File(testFile)
        sha1File.assertIsFile()
        assert new BigInteger(sha1File.text, 16) == getHash(testFile, "SHA1")
    }

    void assertNotPublished() {
        ivyFile.assertDoesNotExist()
    }

    void assertIvyAndJarFilePublished() {
        assertArtifactsPublished(ivyFile.name, jarFile.name)
        assertPublished()
    }

    void assertMetadataAndJarFilePublished() {
        assertArtifactsPublished(ivyFile.name, moduleMetadataFile.name, jarFile.name)
        assertPublished()
    }

    void assertPublished() {
        assert ivyFile.assertIsFile()
        assert parsedIvy.organisation == organisation
        assert parsedIvy.module == module
        assert parsedIvy.revision == revision
    }

    void assertPublishedAsJavaModule() {
        assertPublished()
        assertArtifactsPublished("${module}-${revision}.jar", "ivy-${revision}.xml")
        parsedIvy.expectArtifact(module, "jar").hasAttributes("jar", "jar", ["compile"], null)
    }

    void assertPublishedAsWebModule() {
        assertPublished()
        assertArtifactsPublished("${module}-${revision}.war", "ivy-${revision}.xml")
        parsedIvy.expectArtifact(module, "war").hasAttributes("war", "war", ["master"])
    }

    void assertPublishedAsEarModule() {
        assertPublished()
        assertArtifactsPublished("${module}-${revision}.ear", "ivy-${revision}.xml")
        parsedIvy.expectArtifact(module, "ear").hasAttributes("ear", "ear", ["master"])
    }

    interface IvyModuleArtifact extends ModuleArtifact {
        Map<String, String> getIvyTokens()
    }
}
