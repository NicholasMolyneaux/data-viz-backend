#Back-end server for visualizing pedestrian tracking data
This repo contains the code for the back-end server which process and serves the data to the front-end visualization.
There are three main tasks:
- receive a file from the website and store it somewhere
- process the data to pre-compute as much statistical and aggregate information as possible
- provide an API to call the data

This project uses the play framework (https://www.playframework.com/) in scala for accomplishing the above tasks.
As it was the first time I have used such a framework, the architecture and implementation of the back-end server can definitely be improved.
Many online examples have been used for guidance therefore resemblance between the present code and some material available online is expected.
Nevertheless, all of the work on this repo has been accomplished specifically for the project given in the "Data Visualization" course taught at EPFL.

##Structure
There are two direcroties of interest. The first is the conf folder which contains the configuration for the DB connection and the routes to expose in the API.
The second folder of interest is the app folder which contains all the code. This is the directory structure provided by the play framework.

### conf/
The routes file contains the list of the possible calls which can be made to the API. This file is the processed by the play framework to generate the corresponding scala code.
The application configuration is also found here. The various paths, passwords and connection details are located here.

### app/
The main code of the server is located here. A quick overview of the folders and important files is provided below.
##### controllers/
These are are the classes which handle the API calls. They fetch the data from the DB and turn it into JSON before sending in.
##### models/
The various objects (classes) which should be sent to the front end or saved in the DB are defined here.
The other very important element here is the defintion of the interface which the DB. 
##### processing/
The two processing steps are defined here. They are called at regular intervals and check if files must be processed.
##### upload/
Classes for receiving files from the website. They deal with the POST actions and save the files in the dedicated directories.

## Requirements
- sbt (and scala).
- PostgreSQL database for storing the data. The DB needs to be setup manually.
- Website from https://github.com/NicholasMolyneaux/data-viz hosted somewhere.
