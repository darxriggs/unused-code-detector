// Run on 'cabbage' node.
node('cabbage') {
   // java (jdk) and mvn are supposed to be in the path env of the node running the job

   // Mark the code build 'stage'....
   stage 'Build'
   // Run the maven build
   sh "mvn clean package exec:java"

   // Mark the archive 'stage'....
   stage 'Archive'
   archive 'target/*.html'
}
