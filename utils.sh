TEST_FILE="test.txt"

replace_text() {
	local file="$1"
	local old_text="$2"
	local new_text="$3"
	if [[ "$OSTYPE" == "darwin"* ]]; then
		sed -i '' "s/$old_text/$new_text/g" "$file"
	else
		sed -i "s/$old_text/$new_text/g" "$file"
	fi
}

add_text_with_a_space_before() {
	local file="$1"
	local text="$2"
	if [[ "$OSTYPE" == "darwin"* ]]; then
		sed -i '' "s/$text/$text/g" "$file"
	else
		sed -i "s/$text/$text/g" "$file"
	fi
}

add_text_with_a_space_after() {
	local file="$1"
	local text="$2"
	if [[ "$OSTYPE" == "darwin"* ]]; then
		sed -i '' "s/$text/$text/g" "$file"
	else
		sed -i "s/$text/$text/g" "$file"
	fi
}

add_yaml_array_item_with_a_space_before() {
	local file="$1"
	local text="$2"
	if [[ "$OSTYPE" == "darwin"* ]]; then
		sed -i '' "s/$text/$text/g" "$file"
	else
		sed -i "s/$text/$text/g" "$file"
	fi
}

remove_a_line_begin_with() {
	local file="$1"
	local text="$2"
	# Delete any line that begins with the given word (optionally indented)
	# Uses extended regex and word boundary to avoid partial matches
	if [[ "$OSTYPE" == "darwin"* ]]; then
		# BSD/macOS sed: use [[:<:]] and [[:>:]] for word boundaries
		sed -i '' -E "/^[[:space:]]*[[:<:]]${text//\//\\/}[[:>:]].*/d" "$file"
	else
		# GNU sed: \b is a word boundary
		sed -i -E "/^[[:space:]]*${text//\//\\/}\\b.*/d" "$file"
	fi
}

add_line_after() {
	local search="$1"  # e.g. 'namePrefix: "test"'
	local newline="$2" # e.g. 'nameSuffix: "-dev"'
	local file="$3"    # e.g. 'yourfile.yaml'

	if [[ -z "$search" || -z "$newline" || -z "$file" ]]; then
		echo "Usage: add_line_after <search> <newline> <file>"
		return 1
	fi

	if [[ ! -f "$file" ]]; then
		echo "Error: File not found: $file"
		return 1
	fi

	case "$(uname -s)" in
	Linux)
		sed -i "/${search}/a ${newline}" "$file"
		;;
	Darwin)
		# macOS requires a literal newline right after a\
		sed -i '' "/${search}/a\\ ${newline}"
		"$file"
		;;
	*)
		echo "Unsupported OS: $(uname -s)"
		return 1
		;;
	esac
}

# replace_text "$TEST_FILE" "old_text" "new_text"
# add_text_with_a_space_before "$TEST_FILE" "text"
# add_text_with_a_space_after "$TEST_FILE" "text"
#add_yaml_array_item_with_a_space_before "$TEST_FILE" "text"
add_line_after 'namePrefix: "test"' 'nameSuffix: "-dev"' "$TEST_FILE"
