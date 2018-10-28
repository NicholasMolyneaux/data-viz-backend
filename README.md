#Steps for installing backend environment on local laptop for dev#
## Installing postgreSQL ##
sudo apt-get install postgresql-9.5 -> no problems

Change login rights:
http://suite.opengeo.org/docs/latest/dataadmin/pgGettingStarted/firstconnect.html
Password: DataVizDev

## Install postgREST -> Obsolete, use play framework instead ##
follow step 3 of:
http://postgrest.org/en/v5.1/tutorials/tut0.html

## Install play framework ##
Just download from play website and enjoy.. Documentation is bad but I got hings working.

# Environment #

data
   - uploaded
   - processed

play framework runs processing code every X minutes and checks if files to process in uploaded dir.
Renames them so they don't get processed twice at the same time. 


## Apache server ##
https://www.liquidweb.com/kb/how-to-install-apache-on-centos-7/