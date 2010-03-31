Apache Sling Initial Configuration Loader

This bundle provides initial configuration loading through bundles.

This bundle initialize configuration or factory configurations from the bundles are 
ACTIVE or became active. 
Any ACTIVE bundles that have Felix-Initial-Configuration entry in 
META-INF/MANIFEST.MF are processing. The Felix-Initial-Configuration contain 
path entries, thats are processed by this bundle. This bundle scan the path entries
for *.cfg - which is configurations for bundles (For more information check the 
fileinstall, the file format is same). In the processing of bundles the original 
configuration (if have) are saved in the "user.dir". When the bundle's status
changes from ACTIVE, the original config is restored (or if haven't got, deleted)

Example:

The bundle contains the following files:

- META-INF/MANIFEST.MF

Manifest-Version: 1.0
Felix-Initial-Configuration: SLING-INF/configuration
Built-By: robson
Build-Jdk: 1.6.0_17
Bundle-Version: 1.0.0
Tool: Bnd-0.0.357
Bundle-Name: liveSense Configuration Load Sample
Bnd-LastModified: 1266594965213
Created-By: Apache Maven Bundle Plugin
Bundle-ManifestVersion: 2
Bundle-Description: liveSense Configuration Load sample
Bundle-SymbolicName: org.liveSense.org.liveSense.sample.configurationLoad


- SLING-INF/configuration/org.apache.sling.fsprovider.internal.FsResourceProvider-ROOT.cfg
provider.roots=/fsroot
provider.file=/


When this package becomes active, the configloader creates an FsResourceProvider instance with 
the parameters. When the package stop, the FsResolverProvider instance deletes.



Getting Started
===============

This component uses a Maven 2 (http://maven.apache.org/) build
environment. It requires a Java 5 JDK (or higher) and Maven (http://maven.apache.org/)
2.0.7 or later. We recommend to use the latest Maven version.

If you have Maven 2 installed, you can compile and
package the jar using the following command:

    mvn package

See the Maven 2 documentation for other build features.
