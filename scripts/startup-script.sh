#!/bin/bash

# Update the instance
sudo apt-get update
sudo apt-get upgrade -y

# Install Java (OpenJDK 11 in this example)
sudo apt-get install -y openjdk-17-jre

# Create a directory for the app
APP_DIR="/opt/myapp"
sudo mkdir -p $APP_DIR
sudo chmod 777 $APP_DIR

BASE_URL="https://ela.kpi.ua/"
BUCKET_NAME="llm-raw-documents"
APP_BUCKET_NAME="processor-bin"
FILES_DIR="/files/"
PROJECT_ID="diploma-llm"
DATABASE_ID="(default)"
COLLECTION_ID="raw-documents"
RETRIES=3


# Copy the app.jar from GCS bucket
gsutil cp gs://$APP_BUCKET_NAME/app.jar $APP_DIR/

# Navigate to the app directory
cd $APP_DIR

# Run the application
# (Assuming the application does not need additional parameters or environment variables)
java -jar app.jar $BASE_URL $FILES_DIR $BUCKET_NAME $PROJECT_ID $DATABASE_ID $COLLECTION_ID $RETRIES

# Shutdown the instance after execution is complete
sudo shutdown -h now