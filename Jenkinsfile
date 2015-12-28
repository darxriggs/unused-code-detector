// Run on 'cabbage' node.
node('cabbage') {
   // Get the maven tool.
   // ** NOTE: This 'maven3' maven tool must be configured
   // **       in the global configuration.           
   def mvnHome = tool 'maven3'

   // Mark the code build 'stage'....
   stage 'Build'
   // Run the maven build
   sh "${mvnHome}/bin/mvn clean package exec:java"

   // Mark the archive 'stage'....
   stage 'Archive'
   archive 'target/*.html'
}
