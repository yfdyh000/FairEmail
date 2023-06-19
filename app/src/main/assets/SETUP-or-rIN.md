# ସେଟଅପ୍ ସହାୟତା

Setting up FairEmail is fairly simple. ଯଦି ଆପଣ ଇମେଲ୍ ପ୍ରେରଣ କରିବାକୁ ଚାହାଁନ୍ତି ତେବେ ଆପଣଙ୍କୁ ଅତି କମରେ ଗୋଟିଏ ଆକାଉଣ୍ଟ୍ ଏବଂ ଇମେଲ୍ ପ୍ରାପ୍ତ କରିବାକୁ ଅତିକମରେ ଗୋଟିଏ ପରିଚୟ ଯୋଡ଼ିବାକୁ ପଡ଼ିବ। The quick setup will add an account and an identity in one go for most major providers.

## Requirements

An internet connection is required to set up accounts and identities.

## Quick setup

Just select the appropriate provider or *Other provider* and enter your name, email address and password and tap *Check*.

This will work for most email providers.

If the quick setup doesn't work, you'll need to set up an account and an identity manually, see below for instructions.

## Set up account - to receive email

To add an account, tap *Manual setup and more options*, tap *Accounts* and tap the 'plus' button at the bottom and select IMAP (or POP3). Select a provider from the list, enter the username, which is mostly your email address and enter your password. Tap *Check* to let FairEmail connect to the email server and fetch a list of system folders. After reviewing the system folder selection you can add the account by tapping *Save*.

If your provider is not in the list of providers, there are thousands of providers, select *Custom*. ଡୋମେନ୍ ନାମ ପ୍ରଵେଶ କରନ୍ତୁ, ଉଦାହରଣ ସ୍ୱରୂପ *gmail.com* ଏଵଂ *ସେଟିଂ ପାଆନ୍ତୁ* ଉପରେ ଟ୍ୟାପ୍ କରନ୍ତୁ। If your provider supports [auto-discovery](https://tools.ietf.org/html/rfc6186), FairEmail will fill in the host name and port number, else check the setup instructions of your provider for the right IMAP host name, port number and encryption protocol (SSL/TLS or STARTTLS). For more about this, please see [here](https://github.com/M66B/FairEmail/blob/master/FAQ.md#authorizing-accounts).

## Set up identity - to send email

Similarly, to add an identity, tap *Manual setup and more options*, tap *Identities* and tap the 'plus' button at the bottom. Enter the name you want to appear in the from address of the emails you send and select a linked account. Tap *Save* to add the identity.

If the account was configured manually, you likely need to configure the identity manually too. ଡୋମେନ୍ ନାମ ପ୍ରଵେଶ କରନ୍ତୁ, ଉଦାହରଣ ସ୍ୱରୂପ *gmail.com* ଏଵଂ *ସେଟିଂ ପାଆନ୍ତୁ* ଉପରେ ଟ୍ୟାପ୍ କରନ୍ତୁ। If your provider supports [auto-discovery](https://tools.ietf.org/html/rfc6186), FairEmail will fill in the host name and port number, else check the setup instructions of your provider for the right SMTP host name, port number and encryption protocol (SSL/TLS or STARTTLS).

See [this FAQ](https://github.com/M66B/FairEmail/blob/master/FAQ.md#FAQ9) about using aliases.

## Grant permissions - to access contact information

If you want to lookup email addresses, have contact photos shown, etc, you'll need to grant permission to read contact information to FairEmail. Just tap *Grant* and select *Allow*.

## Setup battery optimizations - to continuously receive emails

On recent Android versions, Android will put apps to sleep when the screen is off for some time to reduce battery usage. ଯଦି ଆପଣ ବିଳମ୍ବ ନକରି ନୂଆ ଇମେଲ୍ ପ୍ରାପ୍ତ କରିବାକୁ ଚାହାଁନ୍ତି, ତେବେ ଆପଣ FairEmail ପାଇଁ ବ୍ୟାଟେରୀ ଅପ୍ଟିମାଇଜେସନ୍ ଅକ୍ଷମ କରିଦେବା ଉଚିତ୍। Tap *Manage* and follow the instructions.

## Questions or problems

ଯଦି ଆପଣଙ୍କର ଏକ ପ୍ରଶ୍ନ କିମ୍ୱା ସମସ୍ୟା ଅଛି, ଦୟାକରି ସହାୟତା ପାଇଁ [ଏଠାରେ ଦେଖନ୍ତୁ](https://github.com/M66B/FairEmail/blob/master/FAQ.md)।